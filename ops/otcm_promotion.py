#!/usr/bin/env python3
"""Explicit OTCM production promotion; inspection never changes remote state."""
import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import urllib.parse
import zipfile
import legacy_integration_diagnostics as diagnostics

APP = pathlib.Path('/www/wwwroot/yiyuanjava')
WEB = pathlib.Path('/www/wwwroot/www.otcm.app')
SERVICE = 'yiyuanjava'
TARGET = '43.166.154.98'


def run(args, **kwargs):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, **kwargs)
    if result.returncode:
        raise RuntimeError('Command failed: ' + pathlib.Path(args[0]).name)
    return result.stdout.decode().strip()


def digest(path):
    h = hashlib.sha256()
    with pathlib.Path(path).open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(part)
    return h.hexdigest()


def runtime():
    pid = int(run(['systemctl', 'show', SERVICE, '--property=MainPID', '--value']))
    if pid <= 0:
        raise ValueError('Clinic service is not running')
    proc = pathlib.Path('/proc') / str(pid)
    cwd = (proc / 'cwd').resolve()
    assert cwd == APP or APP in cwd.parents, 'Unexpected service directory'
    args = (proc / 'cmdline').read_bytes().decode().split('\0')
    environment = dict(line.split('=', 1) for line in (proc / 'environ').read_bytes().decode().split('\0') if '=' in line)
    jar = pathlib.Path(args[args.index('-jar') + 1])
    jar = (jar if jar.is_absolute() else cwd / jar).resolve()
    assert APP in jar.parents, 'Unexpected service JAR'
    with zipfile.ZipFile(jar) as archive:
        keys = set()
        for name in archive.namelist():
            if re.fullmatch(r'BOOT-INF/classes/application(?:-[\w-]+)?\.(?:yml|yaml|properties)', name):
                keys.update(diagnostics.parse_config(archive.read(name).decode('utf-8-sig'), pathlib.Path(name).suffix))
    keys.update(['ruoyi.profile', 'public.app-base-url', 'tcm.instance-id', 'tcm.smtp.key-file', 'token.secret',
                 'file-access.base-url', 'file-access.ttl-seconds', 'tcm.file.access-base-url', 'file.access-base-url',
                 'tcm.file.storage-root', 'tcm.r2.object-prefix'])
    diagnostics.CONFIG_KEYS.update(keys)
    properties, summary = diagnostics.runtime_configuration()
    return {'pid': pid, 'cwd': cwd, 'jar': jar, 'args': args, 'environment': environment,
            'properties': properties, 'summary': summary}


def mysql(runtime_state):
    props = runtime_state['properties']
    jdbc = props.get('spring.datasource.druid.master.url') or props.get('spring.datasource.url', '')
    assert jdbc.startswith('jdbc:mysql://'), 'Unsupported database'
    parsed = urllib.parse.urlsplit(jdbc[5:])
    database = parsed.path.lstrip('/')
    assert parsed.hostname in ('localhost', '127.0.0.1', '::1') and re.fullmatch(r'[A-Za-z0-9_]+', database), 'Unexpected database'
    username = props.get('spring.datasource.druid.master.username') or props.get('spring.datasource.username')
    password = props.get('spring.datasource.druid.master.password') or props.get('spring.datasource.password') or ''
    env = os.environ.copy()
    env['MYSQL_PWD'] = password
    options = ['--protocol=TCP', '--host=' + parsed.hostname, '--port=' + str(parsed.port or 3306), '--user=' + username]
    return database, env, options


def query(state, sql):
    database, env, options = mysql(state)
    return run(['mysql'] + options + ['--batch', '--skip-column-names', '--default-character-set=utf8mb4', database],
               input=('START TRANSACTION READ ONLY; ' + sql + '; ROLLBACK;').encode(), env=env)


def notification_summary(state):
    """Only grouped counts leave MySQL; no IDs, patients, payloads or recipients."""
    clinic_now = run(['date', '+%Y-%m-%d %H:%M:%S'], env=dict(os.environ, TZ='America/Toronto'))
    assert re.fullmatch(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', clinic_now)
    now = "CAST('" + clinic_now + "' AS DATETIME)"
    instance = state['properties'].get('tcm.instance-id', '')
    instance_sql = "CONVERT(X'" + instance.encode().hex() + "' USING utf8mb4)" if instance else "''"

    def value(key):
        return "COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j,'$." + key + "')),'null'),'')"

    def missing(key):
        return "TRIM(" + value(key) + ")=''"

    base = """SELECT start_time,end_time,
        CASE WHEN JSON_VALID(payload) THEN payload ELSE '{}' END AS j,
        CASE WHEN LOWER(TRIM(COALESCE(status,''))) IN ('booked','confirmed','completed','cancelled')
             THEN LOWER(TRIM(status)) ELSE 'other' END AS status_class,
        (TRIM(COALESCE(id,''))<>'' AND TRIM(COALESCE(patient_id,''))<>''
         AND LOWER(TRIM(COALESCE(service_type,'')))<>'time_block') AS patient_appointment
        FROM tcm_appointment"""
    owner = value('notificationOwnerInstance')
    classified = "SELECT b.*,CASE WHEN TRIM(" + owner + ")='' THEN 'legacy_unowned' WHEN BINARY " + owner + "=BINARY " + instance_sql + " THEN 'current_instance' ELSE 'other_instance' END AS owner_class FROM (" + base + ") b"
    eligible = "patient_appointment AND owner_class<>'other_instance'"
    care = eligible + " AND status_class IN ('booked','confirmed','completed')"
    # Mapped start/end columns are DATETIME. Completion is only a legacy JSON fallback.
    end = "COALESCE(end_time,STR_TO_DATE(REPLACE(" + value('treatmentCompletedAt') + ",'T',' '),'%Y-%m-%d %H:%i:%s'))"
    aftercare = care + " AND " + missing('aftercareEmailSentAt') + " AND " + end + "<=DATE_SUB(" + now + ",INTERVAL 1 HOUR)"
    followup = care + " AND " + missing('followUpEmailSentAt') + " AND " + end + "<=DATE_SUB(" + now + ",INTERVAL 3 DAY)"
    reminder = eligible + " AND status_class IN ('booked','confirmed') AND " + missing('reminderEmailSentAt') + " AND start_time>" + now + " AND start_time<=DATE_ADD(" + now + ",INTERVAL 24 HOUR)"
    pending = '(' + ' OR '.join('NOT (' + missing(key + 'PendingAt') + ')' for key in ('aftercareEmailSentAt', 'followUpEmailSentAt', 'reminderEmailSentAt')) + ')'
    outbox = "COALESCE(JSON_LENGTH(JSON_EXTRACT(j,'$.notificationOutbox')),0)>0"
    fields = [
        ('appointments', 'TRUE'), ('patientAppointments', 'patient_appointment'),
        ('aftercareDueCandidates', aftercare), ('followUpDueCandidates', followup),
        ('reminderDueCandidates', reminder),
        ('aftercareOlderThan30Days', aftercare + ' AND ' + end + '<DATE_SUB(' + now + ',INTERVAL 30 DAY)'),
        ('followUpOlderThan30Days', followup + ' AND ' + end + '<DATE_SUB(' + now + ',INTERVAL 30 DAY)'),
        ('withScheduledPendingClaims', eligible + ' AND ' + pending),
        ('withNotificationOutbox', eligible + ' AND ' + outbox),
    ]
    sql = 'SELECT owner_class,status_class,' + ','.join('SUM(CASE WHEN ' + condition + ' THEN 1 ELSE 0 END)' for _, condition in fields)
    sql += ' FROM (' + classified + ') a GROUP BY owner_class,status_class ORDER BY owner_class,status_class'
    rows = []
    for line in query(state, sql).splitlines():
        parts = line.split('\t')
        if len(parts) != len(fields) + 2:
            raise ValueError('Unexpected notification aggregate shape')
        row = {'ownerClass': parts[0], 'status': parts[1]}
        row.update({name: int(count) for (name, _), count in zip(fields, parts[2:])})
        rows.append(row)
    return {
        'clinicTime': clinic_now, 'timezone': 'America/Toronto', 'groups': rows,
        'totals': {name: sum(row[name] for row in rows) for name, _ in fields},
        'firstScanAfterSeconds': 60, 'scanIntervalSeconds': 300, 'pendingClaimRetryMinutes': 10,
        'scope': 'Due candidates only; recipient availability and active pending-claim suppression are not evaluated. No messages sent.',
    }


def inspect():
    state = runtime()
    props = state['properties']
    database, _, _ = mysql(state)
    schema = query(state, 'SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS '
                   'WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION')
    tables = sorted({line.split('\t')[0] for line in schema.splitlines()})
    counts = {}
    for table in tables:
        assert re.fullmatch(r'[A-Za-z0-9_]+', table)
        if table.startswith('tcm_') or table in ('sys_user', 'sys_role', 'sys_user_role'):
            counts[table] = int(query(state, 'SELECT COUNT(*) FROM `' + table + '`'))
    upload = pathlib.Path(props.get('ruoyi.profile', ''))
    if not upload.is_absolute():
        upload = (state['cwd'] / upload).resolve()
    paths = [upload, upload.parent / 'hospital-private', upload.parent / '.otcm',
             APP / 'hospital-private', APP / '.otcm']
    configurations = [str(p) for p in APP.iterdir() if p.is_file() and p.suffix in ('.env', '.properties', '.yml', '.yaml')]
    try:
        notifications = notification_summary(state)
    except Exception as error:
        notifications = {'available': False, 'errorType': type(error).__name__}
    result = {
        'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'readOnly': True,
        'target': TARGET, 'service': state['summary'], 'cwd': str(state['cwd']), 'jarPath': str(state['jar']),
        'javaVersion': subprocess.run([state['args'][0], '-version'], stdout=subprocess.PIPE, stderr=subprocess.PIPE).stderr.decode().splitlines()[0],
        'serviceUser': run(['systemctl', 'show', SERVICE, '--property=User', '--value']) or 'root',
        'serviceUnit': run(['systemctl', 'show', SERVICE, '--property=FragmentPath', '--value']),
        'serviceDropIns': run(['systemctl', 'show', SERVICE, '--property=DropInPaths', '--value']),
        'externalConfigurationPaths': configurations,
        'environmentKeyNames': sorted(k for k in state['environment'] if re.match(r'^(SPRING|TCM|RUOYI|R2|B2|DEEPSEEK|STRIPE|PUBLIC|FILE|TOKEN|DB|REDIS)_', k)),
        'effectiveNonSecretConfiguration': {k: props.get(k, '') for k in ['ruoyi.profile', 'public.app-base-url', 'file-access.base-url', 'tcm.instance-id', 'tcm.smtp.key-file', 'tcm.r2.object-prefix', 'tcm.backup.b2.file-prefix']},
        'effectiveSecretPresence': {k: bool(props.get(k)) for k in ['token.secret', 'deepseek.api-key', 'spring.mail.password', 'stripe.secret-key', 'stripe.webhook-secret', 'tcm.r2.access-key-id', 'tcm.r2.secret-access-key']},
        'database': database, 'rowCounts': counts, 'schema': [line.split('\t') for line in schema.splitlines()],
        'notifications': notifications,
        'storage': [{'path': str(p), 'exists': p.exists(), 'sizeKiB': int(run(['du', '-sk', str(p)]).split()[0]) if p.exists() else 0} for p in dict.fromkeys(paths)],
        'webRoot': str(WEB), 'frontendVersion': json.loads((WEB / 'version.json').read_text()),
        'freeBytes': shutil.disk_usage(APP).free,
        'fontAvailable': pathlib.Path('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc').is_file(),
        'nginxClinicConfig': [str(p) for p in pathlib.Path('/www/server/panel/vhost/nginx').glob('*otcm*.conf')],
    }
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', required=True, choices=[TARGET])
    parser.add_argument('--mode', choices=['inspect'], default='inspect')
    args = parser.parse_args()
    try:
        print(json.dumps(inspect(), ensure_ascii=False, indent=2))
        return 0
    except Exception as error:
        print(json.dumps({'ok': False, 'errorType': type(error).__name__}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
