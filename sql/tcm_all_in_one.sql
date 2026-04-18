-- ============================================================
-- TCM 全量初始化整合脚本
-- 说明：整合基础库、初始化与历史升级脚本
-- 用途：新库直接单文件初始化
-- 备注：已移除当前运行配置未使用的 quartz.sql / QRTZ_* 表
-- ============================================================
-- >>>>>>> BEGIN ry_20250522.sql
-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试员');


-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '1', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '4', 'http://ruoyi.vip', null, '', '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', sysdate(), '', null, '若依官网地址');
-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');


-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');


-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');


-- ----------------------------
-- 18、代码生成业务表
-- ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- ----------------------------
-- 19、代码生成业务表字段
-- ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';
-- <<<<<<< END ry_20250522.sql

-- >>>>>>> BEGIN tcm_init.sql
-- =============================================
-- TCM 中医诊所管理系统 - 数据库初始化脚本
-- 在执行 ry_20250522.sql 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 扩展 sys_user 表（幂等，重复执行不报错）
-- ----------------------------
ALTER TABLE sys_user MODIFY COLUMN user_name varchar(50) NOT NULL COMMENT '用户账号';

-- branch_ids
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'branch_ids');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE sys_user ADD COLUMN branch_ids varchar(500) DEFAULT NULL COMMENT ''关联分店ID(JSON数组)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- assigned_to
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'assigned_to');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE sys_user ADD COLUMN assigned_to bigint(20) DEFAULT NULL COMMENT ''学徒分配给的医师ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 2. 创建业务表
-- ----------------------------

DROP TABLE IF EXISTS tcm_patient;
CREATE TABLE tcm_patient (
  id              varchar(64)   NOT NULL                  COMMENT '病人ID',
  name            varchar(100)  DEFAULT ''                COMMENT '姓名',
  first_name      varchar(50)   DEFAULT ''                COMMENT '名',
  last_name       varchar(50)   DEFAULT ''                COMMENT '姓',
  email           varchar(100)  DEFAULT ''                COMMENT '邮箱',
  phone           varchar(30)   DEFAULT ''                COMMENT '电话',
  practitioner_id varchar(64)   DEFAULT NULL              COMMENT '主治医师ID',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否有效',
  consent_signed  tinyint(1)    DEFAULT 0                 COMMENT '是否签署同意书',
  consent_signed_at datetime    DEFAULT NULL              COMMENT '同意书签署时间',
  merged_into     varchar(64)   DEFAULT NULL              COMMENT '合并到的病人ID',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  payload         longtext      DEFAULT NULL              COMMENT '完整病人信息(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_patient_practitioner (practitioner_id),
  KEY idx_patient_deleted (deleted_at),
  KEY idx_patient_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病人档案表';

DROP TABLE IF EXISTS tcm_appointment;
CREATE TABLE tcm_appointment (
  id              varchar(64)   NOT NULL                  COMMENT '预约ID',
  patient_id      varchar(64)   DEFAULT NULL              COMMENT '病人ID',
  practitioner_id varchar(64)   DEFAULT NULL              COMMENT '医师ID',
  room_id         varchar(64)   DEFAULT NULL              COMMENT '诊室ID',
  service_type    varchar(64)   DEFAULT NULL              COMMENT '服务类型',
  start_time      datetime      DEFAULT NULL              COMMENT '开始时间',
  end_time        datetime      DEFAULT NULL              COMMENT '结束时间',
  status          varchar(20)   DEFAULT 'booked'          COMMENT '状态',
  branch_id       varchar(64)   DEFAULT NULL              COMMENT '分店ID',
  payload         longtext      DEFAULT NULL              COMMENT '附加数据(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_appt_start (start_time),
  KEY idx_appt_practitioner (practitioner_id),
  KEY idx_appt_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

DROP TABLE IF EXISTS tcm_consultation;
CREATE TABLE tcm_consultation (
  id              varchar(64)   NOT NULL                  COMMENT '记录ID',
  consultation_id varchar(64)   DEFAULT NULL              COMMENT '诊疗编号',
  patient_id      varchar(64)   DEFAULT NULL              COMMENT '病人ID',
  practitioner_id varchar(64)   DEFAULT NULL              COMMENT '医师ID',
  consult_date    date          DEFAULT NULL              COMMENT '就诊日期',
  status          varchar(20)   DEFAULT 'draft'           COMMENT '状态',
  branch_id       varchar(64)   DEFAULT NULL              COMMENT '分店ID',
  locked_at       datetime      DEFAULT NULL              COMMENT '锁定时间',
  version         int(11)       DEFAULT 1                 COMMENT '版本号',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  payload         longtext      DEFAULT NULL              COMMENT '完整诊疗数据(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_consultation_id (consultation_id),
  KEY idx_consult_patient (patient_id),
  KEY idx_consult_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊疗记录表';

DROP TABLE IF EXISTS tcm_consultation_mod;
CREATE TABLE tcm_consultation_mod (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '主键',
  consultation_id varchar(64)   DEFAULT NULL              COMMENT '诊疗记录ID',
  mod_date        datetime      DEFAULT NULL              COMMENT '修改时间',
  mod_type        varchar(30)   DEFAULT NULL              COMMENT '修改类型',
  action          varchar(200)  DEFAULT NULL              COMMENT '操作描述',
  user_id         varchar(64)   DEFAULT NULL              COMMENT '操作用户ID',
  version         int(11)       DEFAULT NULL              COMMENT '版本号',
  changes         longtext      DEFAULT NULL              COMMENT '变更内容(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_mod_consultation (consultation_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='诊疗修改审计表';

DROP TABLE IF EXISTS tcm_inventory_item;
CREATE TABLE tcm_inventory_item (
  id              varchar(64)    NOT NULL                  COMMENT '库存ID',
  name            varchar(100)   NOT NULL                  COMMENT '药品名称',
  category        varchar(30)    DEFAULT NULL              COMMENT '分类',
  unit            varchar(20)    DEFAULT NULL              COMMENT '单位',
  quantity        decimal(12,2)  DEFAULT 0                 COMMENT '库存数量',
  price_per_unit  decimal(10,4)  DEFAULT 0                 COMMENT '单价',
  min_stock_level decimal(12,2)  DEFAULT 0                 COMMENT '最低库存',
  supplier        varchar(100)   DEFAULT NULL              COMMENT '供应商',
  grams_per_packet decimal(8,2)  DEFAULT NULL              COMMENT '每包克数',
  branch_id       varchar(64)    DEFAULT NULL              COMMENT '分店ID',
  is_active       tinyint(1)     DEFAULT 1                 COMMENT '是否有效',
  deleted_at      datetime       DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_inv_category (category),
  KEY idx_inv_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品库存表';

DROP TABLE IF EXISTS tcm_branch;
CREATE TABLE tcm_branch (
  id          varchar(64)   NOT NULL                  COMMENT '分店ID',
  name        varchar(100)  NOT NULL                  COMMENT '分店名称',
  code        varchar(30)   DEFAULT NULL              COMMENT '分店编码',
  address     varchar(300)  DEFAULT NULL              COMMENT '地址',
  phone       varchar(30)   DEFAULT NULL              COMMENT '电话',
  email       varchar(100)  DEFAULT NULL              COMMENT '邮箱',
  manager_id  varchar(64)   DEFAULT NULL              COMMENT '店长ID',
  is_active   tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  is_main     tinyint(1)    DEFAULT 0                 COMMENT '是否总店',
  room_ids    varchar(500)  DEFAULT NULL              COMMENT '诊室ID列表(JSON)',
  create_time datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分店表';

DROP TABLE IF EXISTS tcm_room;
CREATE TABLE tcm_room (
  id          varchar(64)   NOT NULL                  COMMENT '诊室ID',
  name        varchar(100)  NOT NULL                  COMMENT '诊室名称',
  branch_id   varchar(64)   DEFAULT NULL              COMMENT '所属分店ID',
  support_tags text         DEFAULT NULL              COMMENT '支持标签(JSON数组)',
  is_active   tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  create_time datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊室表';

DROP TABLE IF EXISTS tcm_service_type;
CREATE TABLE tcm_service_type (
  service_key       varchar(64)   NOT NULL              COMMENT '服务键名',
  label             varchar(100)  DEFAULT NULL           COMMENT '显示名称',
  duration          int(11)       DEFAULT NULL           COMMENT '总时长(分钟)',
  practitioner_time varchar(32)  DEFAULT NULL           COMMENT '医师用时(分钟或overlap标识)',
  room_required     tinyint(1)    DEFAULT 1              COMMENT '是否需要诊室',
  public_visible    tinyint(1)    DEFAULT 1              COMMENT '是否在公共预订页面显示',
  default_price     decimal(10,2) DEFAULT NULL           COMMENT '默认价格',
  required_tag      varchar(64)   DEFAULT NULL           COMMENT '所需诊室标签',
  update_time       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (service_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务类型表';

DROP TABLE IF EXISTS tcm_clinic_setting;
CREATE TABLE tcm_clinic_setting (
  setting_key   varchar(64)  NOT NULL              COMMENT '配置键',
  setting_value longtext     DEFAULT NULL           COMMENT '配置值(JSON)',
  update_time   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊所配置表';

DROP TABLE IF EXISTS tcm_price_list;
CREATE TABLE tcm_price_list (
  id            varchar(64)   NOT NULL                  COMMENT '价目表ID',
  name          varchar(100)  DEFAULT NULL              COMMENT '名称',
  effective_date date         DEFAULT NULL              COMMENT '生效日期',
  is_active     tinyint(1)    DEFAULT 1                 COMMENT '是否有效',
  items_json    longtext      DEFAULT NULL              COMMENT '项目明细(JSON)',
  create_time   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价目表';

DROP TABLE IF EXISTS tcm_email_log;
CREATE TABLE tcm_email_log (
  id          bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '日志ID',
  to_email    varchar(200)  DEFAULT NULL              COMMENT '收件人',
  subject     varchar(300)  DEFAULT NULL              COMMENT '主题',
  email_type  varchar(50)   DEFAULT NULL              COMMENT '邮件类型',
  body        longtext      DEFAULT NULL              COMMENT '邮件内容',
  sent_at     datetime      DEFAULT NULL              COMMENT '发送时间',
  payload     longtext      DEFAULT NULL              COMMENT '附加数据(JSON)',
  create_time datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='邮件日志表';

DROP TABLE IF EXISTS tcm_patient_file;
CREATE TABLE tcm_patient_file (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '附件ID',
  patient_id      varchar(64)   DEFAULT NULL              COMMENT '病人ID',
  consultation_id varchar(64)   DEFAULT NULL              COMMENT '诊疗记录ID',
  file_type       varchar(30)   DEFAULT NULL              COMMENT '文件类型',
  file_name       varchar(200)  DEFAULT NULL              COMMENT '文件名',
  file_path       varchar(500)  DEFAULT NULL              COMMENT '文件路径',
  upload_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_file_patient (patient_id),
  KEY idx_file_consultation (consultation_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='病人附件表';

-- ----------------------------
-- 3. 插入角色（IGNORE 避免重复执行报错）
-- ----------------------------
INSERT IGNORE INTO sys_role VALUES (100, '中医师', 'practitioner', 5, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '中医师角色');
INSERT IGNORE INTO sys_role VALUES (101, '学徒',   'apprentice',   6, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '学徒角色');
INSERT IGNORE INTO sys_role VALUES (102, '药师',   'pharmacist',   7, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '药师角色');
INSERT IGNORE INTO sys_role VALUES (103, '收银',   'cashier',      8, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '收银角色');

-- ----------------------------
-- 4. 插入菜单（IGNORE 避免重复执行报错）
-- ----------------------------
INSERT IGNORE INTO sys_menu VALUES (2000, '中医诊所', 0, 6, 'tcm', NULL, '', '', 1, 0, 'M', '0', '0', '', 'example', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2001, '病人管理', 2000, 1, 'patient',      'tcm/patient/index',      '', '', 1, 0, 'C', '0', '0', 'tcm:patient:list',      'peoples',  'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2002, '预约管理', 2000, 2, 'appointment',  'tcm/appointment/index',  '', '', 1, 0, 'C', '0', '0', 'tcm:appointment:list',  'date',     'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2003, '诊疗管理', 2000, 3, 'consultation', 'tcm/consultation/index', '', '', 1, 0, 'C', '0', '0', 'tcm:consultation:list', 'edit',     'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2004, '库存管理', 2000, 4, 'inventory',    'tcm/inventory/index',    '', '', 1, 0, 'C', '0', '0', 'tcm:inventory:list',    'shopping', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2005, '分店管理', 2000, 5, 'branch',       'tcm/branch/index',       '', '', 1, 0, 'C', '0', '0', 'tcm:branch:list',       'tree',     'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2006, '诊所设置', 2000, 6, 'settings',     'tcm/settings/index',     '', '', 1, 0, 'C', '0', '0', 'tcm:settings:query',    'system',   'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2007, '邮件日志', 2000, 7, 'email',        'tcm/email/index',        '', '', 1, 0, 'C', '0', '0', 'tcm:email:list',        'email',    'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 病人
INSERT IGNORE INTO sys_menu VALUES (2101, '病人查询',  2001, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2102, '病人新增',  2001, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:add',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2103, '病人修改',  2001, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:edit',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2104, '病人删除',  2001, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:remove',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2105, '病人合并',  2001, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:merge',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2106, '同意书签署',2001, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:consent', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 预约
INSERT IGNORE INTO sys_menu VALUES (2201, '预约查询', 2002, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:query',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2202, '预约新增', 2002, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:add',       '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2203, '预约修改', 2002, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:edit',      '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2204, '预约状态', 2002, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:status',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2205, '时段检查', 2002, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:checkslot', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 诊疗
INSERT IGNORE INTO sys_menu VALUES (2301, '诊疗查询', 2003, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:query',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2302, '诊疗新增', 2003, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:add',      '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2303, '诊疗修改', 2003, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:edit',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2304, '诊疗删除', 2003, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:remove',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2305, '标记完成', 2003, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:complete', '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2306, '标记付费', 2003, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:paid',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2307, '标记配药', 2003, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:dispense', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 库存
INSERT IGNORE INTO sys_menu VALUES (2401, '库存查询', 2004, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2402, '库存新增', 2004, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:add',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2403, '库存修改', 2004, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:edit',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2404, '库存删除', 2004, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:remove',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2405, '库存调整', 2004, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:adjust',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2406, '处方扣减', 2004, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:deduct',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2407, '处方恢复', 2004, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:restore', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 分店
INSERT IGNORE INTO sys_menu VALUES (2501, '分店查询', 2005, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2502, '分店新增', 2005, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2503, '分店修改', 2005, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2504, '分店切换', 2005, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:toggle', '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2505, '分店删除', 2005, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_menu VALUES (2601, '设置修改', 2006, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:settings:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2701, '邮件新增', 2007, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:email:add',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2801, '文件上传', 2000, 8, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:file:upload',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2802, '文件下载', 2000, 9, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:file:download', '#', 'admin', sysdate(), '', NULL, '');

-- ----------------------------
-- 5. 角色-菜单关联
-- ----------------------------
-- admin (1)
INSERT IGNORE INTO sys_role_menu VALUES (1,2000),(1,2001),(1,2002),(1,2003),(1,2004),(1,2005),(1,2006),(1,2007);
INSERT IGNORE INTO sys_role_menu VALUES (1,2101),(1,2102),(1,2103),(1,2104),(1,2105),(1,2106);
INSERT IGNORE INTO sys_role_menu VALUES (1,2201),(1,2202),(1,2203),(1,2204),(1,2205);
INSERT IGNORE INTO sys_role_menu VALUES (1,2301),(1,2302),(1,2303),(1,2304),(1,2305),(1,2306),(1,2307);
INSERT IGNORE INTO sys_role_menu VALUES (1,2401),(1,2402),(1,2403),(1,2404),(1,2405),(1,2406),(1,2407);
INSERT IGNORE INTO sys_role_menu VALUES (1,2501),(1,2502),(1,2503),(1,2504),(1,2505);
INSERT IGNORE INTO sys_role_menu VALUES (1,2601),(1,2701),(1,2801),(1,2802);

-- practitioner (100)
INSERT IGNORE INTO sys_role_menu VALUES (100,2000),(100,2001),(100,2002),(100,2003),(100,2004),(100,2006);
INSERT IGNORE INTO sys_role_menu VALUES (100,2101),(100,2102),(100,2103),(100,2104),(100,2105),(100,2106);
INSERT IGNORE INTO sys_role_menu VALUES (100,2201),(100,2202),(100,2203),(100,2204),(100,2205);
INSERT IGNORE INTO sys_role_menu VALUES (100,2301),(100,2302),(100,2303),(100,2304),(100,2305),(100,2306),(100,2307);
INSERT IGNORE INTO sys_role_menu VALUES (100,2401),(100,2801),(100,2802);

-- apprentice (101)
INSERT IGNORE INTO sys_role_menu VALUES (101,2000),(101,2001),(101,2002),(101,2003);
INSERT IGNORE INTO sys_role_menu VALUES (101,2101),(101,2102),(101,2103),(101,2106);
INSERT IGNORE INTO sys_role_menu VALUES (101,2201),(101,2202),(101,2203),(101,2205);
INSERT IGNORE INTO sys_role_menu VALUES (101,2301);

-- pharmacist (102)
INSERT IGNORE INTO sys_role_menu VALUES (102,2000),(102,2003),(102,2004);
INSERT IGNORE INTO sys_role_menu VALUES (102,2301),(102,2307);
INSERT IGNORE INTO sys_role_menu VALUES (102,2401),(102,2402),(102,2403),(102,2404),(102,2405),(102,2406),(102,2407);

-- cashier (103)
INSERT IGNORE INTO sys_role_menu VALUES (103,2000),(103,2003);
INSERT IGNORE INTO sys_role_menu VALUES (103,2301),(103,2306);

-- ----------------------------
-- 6. 演示用户 (user_name = email)
-- ----------------------------
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (100, 100, 'admin@clinic.com',      '张管理', '00', 'admin@clinic.com',      '13800000001', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM管理员', '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (101, 100, 'doctor@clinic.com',     '李医师', '00', 'doctor@clinic.com',     '13800000002', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM中医师', '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (102, 100, 'doctor2@clinic.com',    '王医师', '00', 'doctor2@clinic.com',    '13800000005', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM中医师', '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (103, 100, 'apprentice@clinic.com', '陈学徒', '00', 'apprentice@clinic.com', '13800000003', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM学徒',   '["branch-main"]', 101);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (104, 100, 'pharmacist@clinic.com', '刘药师', '00', 'pharmacist@clinic.com', '13800000004', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM药师',   '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (105, 100, 'cashier@clinic.com',    '赵收银', '00', 'cashier@clinic.com',    '13800000006', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM收银',   '["branch-main"]', NULL);

INSERT IGNORE INTO sys_user_role VALUES (100, 1);
INSERT IGNORE INTO sys_user_role VALUES (101, 100);
INSERT IGNORE INTO sys_user_role VALUES (102, 100);
INSERT IGNORE INTO sys_user_role VALUES (103, 101);
INSERT IGNORE INTO sys_user_role VALUES (104, 102);
INSERT IGNORE INTO sys_user_role VALUES (105, 103);

-- 确保所有演示用户密码统一为 admin123（防止 INSERT IGNORE 跳过后密码不一致）
UPDATE sys_user SET password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2'
WHERE user_id IN (100, 101, 102, 103, 104, 105);

-- ----------------------------
-- 7. 种子业务数据
-- ----------------------------

-- 分店
INSERT IGNORE INTO tcm_branch VALUES ('branch-main',  '总店',     'MAIN',  '北京市朝阳区建国路88号',     '010-88886666', 'main@clinic.com',  '100', 1, 1, '["room-1","room-2","room-3"]', sysdate(), sysdate());
INSERT IGNORE INTO tcm_branch VALUES ('branch-east',  '东城分店', 'EAST',  '北京市东城区东直门大街12号',   '010-88886667', 'east@clinic.com',  NULL,  1, 0, '[]', sysdate(), sysdate());
INSERT IGNORE INTO tcm_branch VALUES ('branch-south', '南城分店', 'SOUTH', '北京市丰台区南三环路56号',     '010-88886668', 'south@clinic.com', NULL,  1, 0, '[]', sysdate(), sysdate());

-- 诊室（按设计规格：房间1支持全部，房间2支持针灸/推拿/问诊/中药，房间3支持问诊/中药）
INSERT IGNORE INTO tcm_room VALUES ('room-1', '诊疗室一号', 'branch-main', '["moxibustion","acupuncture","tuina","consultation","herbs"]', 1, sysdate(), sysdate());
INSERT IGNORE INTO tcm_room VALUES ('room-2', '诊疗室二号', 'branch-main', '["acupuncture","tuina","consultation","herbs"]', 1, sysdate(), sysdate());
INSERT IGNORE INTO tcm_room VALUES ('room-3', '诊疗室三号', 'branch-main', '["consultation","herbs"]', 1, sysdate(), sysdate());
-- 如诊室已存在则更新标签
UPDATE tcm_room SET support_tags = '["moxibustion","acupuncture","tuina","consultation","herbs"]' WHERE id = 'room-1';
UPDATE tcm_room SET support_tags = '["acupuncture","tuina","consultation","herbs"]' WHERE id = 'room-2';
UPDATE tcm_room SET support_tags = '["consultation","herbs"]' WHERE id = 'room-3';

-- 服务类型
-- 针灸1h: 60min房间占用, practitioner_time由per-practitioner interval决定(设20为默认回退值)
INSERT IGNORE INTO tcm_service_type VALUES ('acupuncture_new',      '针灸1小时',   60, 20, 1, 1, 120.00, 'acupuncture', sysdate());
-- 仅中药: 仅占用overlap时间, 无需房间
INSERT IGNORE INTO tcm_service_type VALUES ('herbs_only',           '仅中药',      20, 20, 0, 1,  60.00, 'herbs', sysdate());
-- 针灸40min: 40min房间占用
INSERT IGNORE INTO tcm_service_type VALUES ('acupuncture_40',       '针灸40分钟',  40, 20, 1, 1, 100.00, 'acupuncture', sysdate());
-- 推拿40min: 推拿师全程占用40min + 40min房间占用(无overlap)
INSERT IGNORE INTO tcm_service_type VALUES ('tuina_40',             '推拿40分钟',  40, 40, 1, 1, 100.00, 'tuina', sysdate());
-- 如服务类型已存在则更新
UPDATE tcm_service_type SET label='针灸1小时', duration=60, practitioner_time=20, room_required=1, default_price=120.00, required_tag='acupuncture' WHERE service_key='acupuncture_new';
UPDATE tcm_service_type SET label='仅中药',    duration=20, practitioner_time=20, room_required=0, default_price=60.00,  required_tag='herbs'       WHERE service_key='herbs_only';
UPDATE tcm_service_type SET label='针灸40分钟', duration=40, practitioner_time=20, room_required=1, default_price=100.00, required_tag='acupuncture' WHERE service_key='acupuncture_40';
UPDATE tcm_service_type SET label='推拿40分钟', duration=40, practitioner_time=40, room_required=1, default_price=100.00, required_tag='tuina'       WHERE service_key='tuina_40';
-- 为已有数据添加 public_visible 列（默认为1=公开显示）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_service_type' AND COLUMN_NAME = 'public_visible');
SET @ddl = IF(@col_exists = 0, "ALTER TABLE tcm_service_type ADD COLUMN public_visible tinyint(1) DEFAULT 1 COMMENT '是否在公共预订页面显示' AFTER room_required", 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- 删除旧的 acupuncture_followup（已合并到 acupuncture_new）
DELETE FROM tcm_service_type WHERE service_key = 'acupuncture_followup';

-- 将 practitioner_time 列从 int 改为 varchar 以支持 overlap1/overlap2 标识
SET @col_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_service_type' AND COLUMN_NAME = 'practitioner_time');
SET @ddl2 = IF(@col_type = 'int', "ALTER TABLE tcm_service_type MODIFY COLUMN practitioner_time varchar(32) DEFAULT NULL COMMENT '医师用时(分钟或overlap标识)'", 'SELECT 1');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 诊所配置
INSERT IGNORE INTO tcm_clinic_setting VALUES ('taxRate',              '0.13',                         sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('practitionerInterval', '20',                           sysdate());
-- per-practitioner overlap intervals: 李医师(101)=20min, 王医师(102)=30min
INSERT IGNORE INTO tcm_clinic_setting VALUES ('practitionerIntervals', '{"101":20,"102":30}',         sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('profitRatio',          '1.0',                          sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('clinicName',           '中医养生堂',                   sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('clinicAddress',        '北京市朝阳区建国路88号',       sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('clinicPhone',          '010-88886666',                  sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('publicBookingAdvanceDays', '15',                        sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('publicBookingDripWindowDays', '7',                      sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('publicBookingDripMinutes', '60',                        sysdate());
-- 如已存在则更新 per-practitioner intervals
UPDATE tcm_clinic_setting SET setting_value = '{"101":20,"102":30}' WHERE setting_key = 'practitionerIntervals';

-- 病人
INSERT IGNORE INTO tcm_patient (id, name, first_name, last_name, email, phone, practitioner_id, is_active, consent_signed, consent_signed_at, payload, create_time)
VALUES ('patient-1', '张三', '三', '张', 'zhangsan@example.com', '13900001111', '101', 1, 1, '2024-02-01 08:30:00',
'{"firstName":"三","lastName":"张","middleName":"","jobTitle":"","accountName":"","emails":["zhangsan@example.com"],"email2":"","email3":"","phone":"13900001111","mobilePhone":"13900001111","businessPhone":"","fax":"","preferredContact":"Any","dateOfBirth":"1985-06-15","gender":"男","address":"北京市朝阳区","addressStreet":"朝阳路802号","addressCity":"北京","addressState":"北京市","addressPostal":"100020","diseaseName":"慢性疲劳综合征","historyAndMedication":"对花粉过敏，既往无重大手术史","notes":"对花粉过敏"}',
'2024-02-01 08:00:00');

INSERT IGNORE INTO tcm_patient (id, name, first_name, last_name, email, phone, practitioner_id, is_active, consent_signed, consent_signed_at, payload, create_time)
VALUES ('patient-2', '李四', '四', '李', 'lisi@example.com', '13900002222', '101', 1, 1, '2024-02-10 09:15:00',
'{"firstName":"四","lastName":"李","middleName":"","jobTitle":"","accountName":"","emails":["lisi@example.com","lisi_work@example.com"],"email2":"lisi_work@example.com","email3":"","phone":"13900002222","mobilePhone":"13900002222","businessPhone":"","fax":"","preferredContact":"Email","dateOfBirth":"1990-11-20","gender":"女","address":"北京市海淀区","addressStreet":"海淀路15号","addressCity":"北京","addressState":"北京市","addressPostal":"100080","diseaseName":"失眠症，月经不调","historyAndMedication":"无药物过敏史","notes":""}',
'2024-02-10 09:00:00');

INSERT IGNORE INTO tcm_patient (id, name, first_name, last_name, email, phone, practitioner_id, is_active, consent_signed, payload, create_time)
VALUES ('patient-3', '王五', '五', '王', 'wangwu@example.com', '13900003333', '102', 1, 0,
'{"firstName":"五","lastName":"王","middleName":"","jobTitle":"","accountName":"","emails":["wangwu@example.com"],"email2":"","email3":"","phone":"13900003333","mobilePhone":"13900003333","businessPhone":"01088881234","fax":"","preferredContact":"Phone","dateOfBirth":"1978-03-08","gender":"男","address":"北京市西城区","addressStreet":"西长安街1号","addressCity":"北京","addressState":"北京市","addressPostal":"100032","diseaseName":"高血压，颈椎病","historyAndMedication":"高血压病史10年，服用降压药","notes":"高血压病史"}',
'2024-03-01 10:00:00');

-- 诊疗记录
INSERT IGNORE INTO tcm_consultation (id, consultation_id, patient_id, practitioner_id, consult_date, status, branch_id, locked_at, version, payload, create_time)
VALUES ('consult-1', 'ORD-00001-A1B2C3', 'patient-1', '101', '2024-02-01', 'paid', 'branch-main', '2024-02-01 16:00:00', 1,
'{"chiefComplaint":"Fatigue 疲劳","chiefComplaintDuration":"1-4 weeks 一个月内","chiefComplaintDescription":"患者主诉头痛、失眠，持续两周，伴有疲劳乏力，工作压力较大。","progressOfDisease":"症状逐渐加重，夜间尤为明显。","summary":"患者主诉头痛、失眠，持续两周，伴有疲劳乏力。","differentiation":"肝郁气滞，心神不宁","treatment":"疏肝解郁，养心安神。针灸配合中药汤剂。","diff":{"coldHeat":"Neither 无","sweat":"Normal 正常","headDiscomfort":"头痛，两侧为主","eye":"","ear":"","nose":"","mouth":"","taste":"Bitter 口苦","bodyDiscomforts":"肩颈僵硬","skinIssues":"","otherExterior":"","chest":"Tightness 胸闷","heart":"Palpitation 心悸","hypochondriac":"Distension 胀","sleep":"Insomnia 失眠","anxietyStress":"工作压力大，容易烦躁","otherChest":"","appetite":"Poor appetit 食欲不振","thirst":"Dry mouth 口干","abdomen":"Bloating 腹胀","otherAbdomen":"","bowelMovement":"Normal 正常","urine":"Normal 正常","otherLowerAbdomen":"","periodCircle":"","periodDuration":"","bloodQuality":"","pms":"","otherFemale":"","pathologicalChannel":"肝经，心经","pathologicalChanges":"肝区压痛，太冲穴有明显酸胀感","pulse":"Wiry 弦","detailedPulse":"弦细，左关尤甚","tongueColor":"Red 红","tongueBody":"Normal 正常","tongueCoating":"Thin yellow 薄黄","otherTongue":"舌尖红","tongueImage":null,"conclusions":[{"name":"肝郁气滞","treatment":"疏肝理气"},{"name":"心神不宁","treatment":"养心安神"}]},"acupuncture":[{"point":"百会","side":"bilateral","notes":"留针20分钟"},{"point":"神门","side":"bilateral","notes":""},{"point":"太冲","side":"bilateral","notes":""},{"point":"合谷","side":"left","notes":""}],"prescriptions":[{"id":"rx-1","direction":"内服 Oral intake","whereToGet":"In-store 店内取药","quantity":7,"preferredUnit":"g","formulaName":"逍遥散加减","items":[{"name":"柴胡","dosage":10,"unit":"g","category":"1. 辛温解表药","guijing":"肝, 胆","nature":"3. 微寒","taste":"Acrid辛, Bitter苦","pricePerUnit":0.15},{"name":"白芍","dosage":15,"unit":"g","category":"40. 补血药","guijing":"肝, 脾","nature":"3. 微寒","taste":"Bitter苦, Sour酸","pricePerUnit":0.12},{"name":"当归","dosage":10,"unit":"g","category":"40. 补血药","guijing":"肝, 心, 脾","nature":"6. 温","taste":"Sweet甜, Acrid辛","pricePerUnit":0.20},{"name":"茯苓","dosage":15,"unit":"g","category":"15. 利水渗湿药","guijing":"脾, 心, 肺","nature":"4. 平","taste":"Sweet甜, Bland淡","pricePerUnit":0.09},{"name":"白术","dosage":10,"unit":"g","category":"38. 补气药","guijing":"脾, 胃","nature":"6. 温","taste":"Bitter苦, Sweet甜","pricePerUnit":0.10},{"name":"甘草","dosage":6,"unit":"g","category":"38. 补气药","guijing":"脾, 肺","nature":"4. 平","taste":"Sweet甜","pricePerUnit":0.06}],"subtotal":14.94,"dispensingCompleted":true}],"herbals":[{"name":"柴胡","dosage":10,"unit":"g"},{"name":"白芍","dosage":15,"unit":"g"},{"name":"当归","dosage":10,"unit":"g"},{"name":"茯苓","dosage":15,"unit":"g"},{"name":"白术","dosage":10,"unit":"g"},{"name":"甘草","dosage":6,"unit":"g"}],"formulaName":"逍遥散加减","prescriptionType":"raw_herbs","prognosis":"预计1-2周后头痛改善，睡眠逐步好转，建议复诊。","feedback":"","previousPrognosisReview":null,"servicePriceList":"2024-02","services":[{"name":"针灸首诊 Basic Acupuncture 60min","price":120,"quantity":1,"manualDiscount":0,"taxable":true},{"name":"中药处方（7剂）","price":280,"quantity":1,"manualDiscount":0,"taxable":true}],"consultationFee":0,"discountType":"none","discountValue":0,"taxable":true,"includeRxAmount":false,"add3rdParty":false,"currency":"CAD","comments":"","totalAmount":400,"taxAmount":52,"totalWithoutTax":400,"documents":[],"invoicePdfUrl":null,"consultationPdfUrl":null,"modifications":[],"parentConsultationId":null}',
'2024-02-01 14:00:00');

INSERT IGNORE INTO tcm_consultation (id, consultation_id, patient_id, practitioner_id, consult_date, status, branch_id, locked_at, version, payload, create_time)
VALUES ('consult-2', 'ORD-00002-D4E5F6', 'patient-1', '101', '2024-02-15', 'paid', 'branch-main', '2024-02-15 16:00:00', 1,
'{"chiefComplaint":"Fatigue 疲劳","chiefComplaintDuration":"1-4 weeks 一个月内","chiefComplaintDescription":"复诊。头痛明显改善，睡眠好转约60%，仍感乏力。","progressOfDisease":"整体好转，乏力症状为主要问题。","summary":"复诊。头痛明显改善，睡眠好转约60%，仍感乏力。","differentiation":"气血两虚为主，肝郁已解","treatment":"补气养血为主，继续针灸治疗。","diff":{"coldHeat":"Neither 无","sweat":"Spontaneous sweating 自汗","headDiscomfort":"偶有轻微头痛","eye":"","ear":"","nose":"","mouth":"","taste":"Normal 正常","bodyDiscomforts":"全身乏力","skinIssues":"","otherExterior":"","chest":"Normal 正常","heart":"Normal 正常","hypochondriac":"None 无","sleep":"Dream-disturbed 多梦","anxietyStress":"","otherChest":"","appetite":"Poor appetit 食欲不振","thirst":"Normal 正常","abdomen":"Normal 正常","otherAbdomen":"","bowelMovement":"Loose 便溏","urine":"Normal 正常","otherLowerAbdomen":"","periodCircle":"","periodDuration":"","bloodQuality":"","pms":"","otherFemale":"","pathologicalChannel":"脾经，胃经","pathologicalChanges":"腹部压痛减轻","pulse":"Thready 细","detailedPulse":"细弱","tongueColor":"Pale 淡白","tongueBody":"Swollen 胖大","tongueCoating":"Thin white 薄白","otherTongue":"舌边有齿痕","tongueImage":null,"conclusions":[{"name":"气血两虚","treatment":"补气养血"},{"name":"脾虚湿困","treatment":"健脾祛湿"}]},"acupuncture":[{"point":"气海","side":"bilateral","notes":""},{"point":"足三里","side":"bilateral","notes":"补法"},{"point":"三阴交","side":"bilateral","notes":""}],"prescriptions":[{"id":"rx-2","direction":"内服 Oral intake","whereToGet":"In-store 店内取药","quantity":7,"preferredUnit":"g","formulaName":"八珍汤加减","items":[{"name":"黄芪","dosage":30,"unit":"g","category":"38. 补气药","guijing":"肺, 脾","nature":"6. 温","taste":"Sweet甜","pricePerUnit":0.08},{"name":"党参","dosage":15,"unit":"g","category":"38. 补气药","guijing":"脾, 肺","nature":"4. 平","taste":"Sweet甜","pricePerUnit":0.12},{"name":"白术","dosage":10,"unit":"g","category":"38. 补气药","guijing":"脾, 胃","nature":"6. 温","taste":"Bitter苦, Sweet甜","pricePerUnit":0.10},{"name":"当归","dosage":10,"unit":"g","category":"40. 补血药","guijing":"肝, 心, 脾","nature":"6. 温","taste":"Sweet甜, Acrid辛","pricePerUnit":0.20},{"name":"熟地黄","dosage":15,"unit":"g","category":"40. 补血药","guijing":"肝, 肾","nature":"3. 微寒","taste":"Sweet甜","pricePerUnit":0.14},{"name":"甘草","dosage":6,"unit":"g","category":"38. 补气药","guijing":"脾, 肺","nature":"4. 平","taste":"Sweet甜","pricePerUnit":0.06}],"subtotal":18.62,"dispensingCompleted":true}],"herbals":[{"name":"黄芪","dosage":30,"unit":"g"},{"name":"党参","dosage":15,"unit":"g"},{"name":"白术","dosage":10,"unit":"g"},{"name":"当归","dosage":10,"unit":"g"},{"name":"熟地黄","dosage":15,"unit":"g"},{"name":"甘草","dosage":6,"unit":"g"}],"formulaName":"八珍汤加减","prescriptionType":"raw_herbs","prognosis":"再坚持两周，乏力症状可显著改善。","feedback":"","previousPrognosisReview":"上次预测头痛会改善——准确，睡眠改善比预期稍慢。","servicePriceList":"2024-02","services":[{"name":"针灸复诊 Supercare Acupuncture 30min","price":80,"quantity":1,"manualDiscount":0,"taxable":true},{"name":"中药处方（7剂）","price":280,"quantity":1,"manualDiscount":0,"taxable":true}],"consultationFee":0,"discountType":"none","discountValue":0,"taxable":true,"includeRxAmount":false,"add3rdParty":false,"currency":"CAD","comments":"","totalAmount":360,"taxAmount":46.8,"totalWithoutTax":360,"documents":[],"invoicePdfUrl":null,"consultationPdfUrl":null,"modifications":[],"parentConsultationId":"consult-1"}',
'2024-02-15 14:30:00');

-- 预约
INSERT IGNORE INTO tcm_appointment (id, patient_id, practitioner_id, room_id, service_type, start_time, end_time, status, branch_id, payload, create_time)
VALUES ('appt-1', 'patient-1', '101', 'room-1', 'acupuncture_40', CONCAT(CURDATE(),' 09:00:00'), CONCAT(CURDATE(),' 09:40:00'), 'confirmed', 'branch-main', '{"intakeFormData":{"chiefComplaint":"乏力改善，但仍有轻微头痛"},"notes":""}', NOW());
INSERT IGNORE INTO tcm_appointment (id, patient_id, practitioner_id, room_id, service_type, start_time, end_time, status, branch_id, payload, create_time)
VALUES ('appt-2', 'patient-2', '101', 'room-2', 'acupuncture_new', CONCAT(CURDATE(),' 10:30:00'), CONCAT(CURDATE(),' 11:30:00'), 'booked', 'branch-main', '{"intakeFormData":{},"notes":"初次就诊"}', NOW());

-- 库存
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-1',  '逍遥散',             'powder',    '包', 50.00,   35.0000, 10.00, '同仁堂',  6.00, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-2',  '六味地黄丸（浓缩粉）','powder',   '包', 8.00,    40.0000, 10.00, '同仁堂',  6.00, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-3',  '补中益气汤',         'powder',    '包', 30.00,   38.0000, 10.00, '康仁堂',  5.00, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-4',  '黄芪',              'raw_herbs', 'g',  2000.00,  0.0800, 500.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-5',  '党参',              'raw_herbs', 'g',  1500.00,  0.1200, 300.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-6',  '白术',              'raw_herbs', 'g',  800.00,   0.1000, 200.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-7',  '茯苓',              'raw_herbs', 'g',  1200.00,  0.0900, 300.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-8',  '柴胡',              'raw_herbs', 'g',  150.00,   0.1500, 200.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-9',  '当归',              'raw_herbs', 'g',  900.00,   0.2000, 200.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-10', '甘草',              'raw_herbs', 'g',  1800.00,  0.0600, 300.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-11', '六味地黄丸',         'pills',     '盒', 25.00,   28.0000, 5.00, '同仁堂',  NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-12', '逍遥丸',             'pills',     '盒', 3.00,    22.0000, 5.00, '同仁堂',  NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-13', '金匮肾气丸',         'pills',     '瓶', 15.00,   35.0000, 5.00, '华佗',    NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
-- <<<<<<< END tcm_init.sql

-- >>>>>>> BEGIN tcm_upgrade_v2.sql
-- =============================================
-- TCM v2 升级脚本 - 补全3项增强功能
-- 1. 同意书邮件链接签署
-- 2. 病人在线问诊表单
-- 3. 文件夹层级展示（前端实现，无需数据库变更）
-- =============================================

-- 1. 同意书令牌字段
ALTER TABLE tcm_patient ADD COLUMN consent_token varchar(64) DEFAULT NULL COMMENT '同意书签署令牌';
ALTER TABLE tcm_patient ADD COLUMN consent_token_expires datetime DEFAULT NULL COMMENT '令牌过期时间';
ALTER TABLE tcm_patient ADD KEY idx_patient_consent_token (consent_token);

-- 2. 问诊表单令牌字段
ALTER TABLE tcm_appointment ADD COLUMN intake_token varchar(64) DEFAULT NULL COMMENT '问诊表单令牌';
ALTER TABLE tcm_appointment ADD COLUMN intake_submitted tinyint(1) DEFAULT 0 COMMENT '表单是否已提交';
ALTER TABLE tcm_appointment ADD KEY idx_appt_intake_token (intake_token);
-- <<<<<<< END tcm_upgrade_v2.sql

-- >>>>>>> BEGIN tcm_upgrade_all.sql
-- ============================================================
-- TCM 全量升级脚本（整合 v3 ~ v7）
-- 说明：按历史升级顺序顺次拼接，保留原始升级脚本同时提供单文件入口
-- 适用于：已执行过 tcm_init.sql + tcm_upgrade_v2.sql 的数据库
-- ============================================================
-- =============================================
-- TCM v3 升级脚本 - 方剂管理 & 供应商管理
-- 在执行 tcm_upgrade_v2.sql 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 供应商表
-- ----------------------------
DROP TABLE IF EXISTS tcm_supplier;
CREATE TABLE tcm_supplier (
  id              varchar(64)   NOT NULL                  COMMENT '供应商ID',
  name            varchar(100)  NOT NULL                  COMMENT '供应商名称',
  contact_person  varchar(100)  DEFAULT NULL              COMMENT '联系人',
  phone           varchar(30)   DEFAULT NULL              COMMENT '电话',
  email           varchar(100)  DEFAULT NULL              COMMENT '邮箱',
  address         varchar(300)  DEFAULT NULL              COMMENT '地址',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_supplier_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ----------------------------
-- 2. 方剂主表
-- ----------------------------
DROP TABLE IF EXISTS tcm_formula_item;
DROP TABLE IF EXISTS tcm_formula;
CREATE TABLE tcm_formula (
  id              varchar(64)   NOT NULL                  COMMENT '方剂ID',
  name            varchar(100)  NOT NULL                  COMMENT '方剂名称',
  category        varchar(100)  DEFAULT NULL              COMMENT '方剂分类',
  description     varchar(500)  DEFAULT NULL              COMMENT '方剂说明/功效',
  source          varchar(200)  DEFAULT NULL              COMMENT '出处/来源',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_formula_name (name),
  KEY idx_formula_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂表';

-- ----------------------------
-- 3. 方剂药材明细表
-- ----------------------------
CREATE TABLE tcm_formula_item (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '主键',
  formula_id      varchar(64)   NOT NULL                  COMMENT '方剂ID',
  herb_name       varchar(100)  NOT NULL                  COMMENT '药材名称',
  dosage          decimal(10,2) DEFAULT 0                 COMMENT '默认剂量',
  unit            varchar(20)   DEFAULT 'g'               COMMENT '单位',
  sort_order      int(11)       DEFAULT 0                 COMMENT '排序',
  notes           varchar(200)  DEFAULT NULL              COMMENT '备注（如炮制方法）',
  PRIMARY KEY (id),
  KEY idx_formula_item_fid (formula_id),
  CONSTRAINT fk_formula_item_formula FOREIGN KEY (formula_id) REFERENCES tcm_formula (id) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='方剂药材明细表';

-- ----------------------------
-- 4. 库存表增加 supplier_id 字段
-- ----------------------------
ALTER TABLE tcm_inventory_item ADD COLUMN supplier_id varchar(64) DEFAULT NULL COMMENT '供应商ID' AFTER supplier;
ALTER TABLE tcm_inventory_item ADD KEY idx_inv_supplier (supplier_id);

-- ----------------------------
-- 5. 种子数据 - 供应商
-- ----------------------------
INSERT INTO tcm_supplier (id, name, contact_person, phone, email, address, notes) VALUES
('supplier-1', '同仁堂',   '张经理', '010-65135566', 'tongren@example.com',  '北京市东城区东兴隆街52号', '百年老字号'),
('supplier-2', '康仁堂',   '李经理', '010-82863366', 'kangren@example.com',  '北京市昌平区科技园区',     '中药配方颗粒供应商'),
('supplier-3', '本草药材', '王经理', '010-67891234', 'bencao@example.com',   '北京市丰台区南苑路',       '散装草药供应商'),
('supplier-4', '华佗',     '赵经理', '020-88881234', 'huatuo@example.com',   '广东省广州市天河区',       '成药供应商');

-- 回填已有库存数据的 supplier_id
UPDATE tcm_inventory_item SET supplier_id = 'supplier-1' WHERE supplier = '同仁堂';
UPDATE tcm_inventory_item SET supplier_id = 'supplier-2' WHERE supplier = '康仁堂';
UPDATE tcm_inventory_item SET supplier_id = 'supplier-3' WHERE supplier = '本草药材';
UPDATE tcm_inventory_item SET supplier_id = 'supplier-4' WHERE supplier = '华佗';

-- ----------------------------
-- 6. 种子数据 - 方剂
-- ----------------------------
INSERT INTO tcm_formula (id, name, category, description, source) VALUES
('formula-1', '四君子汤',   '补益剂', '益气健脾',               '《太平惠民和剂局方》'),
('formula-2', '六味地黄丸', '补益剂', '滋补肝肾',               '《小儿药证直诀》'),
('formula-3', '逍遥散',     '和解剂', '疏肝解郁，养血健脾',     '《太平惠民和剂局方》'),
('formula-4', '补中益气汤', '补益剂', '补中益气，升阳举陷',     '《内外伤辨惑论》'),
('formula-5', '八珍汤',     '补益剂', '气血双补',               '《正体类要》'),
('formula-6', '小柴胡汤',   '和解剂', '和解少阳',               '《伤寒论》'),
('formula-7', '桂枝汤',     '解表剂', '解肌发表，调和营卫',     '《伤寒论》'),
('formula-8', '金匮肾气丸', '补益剂', '温补肾阳',               '《金匮要略》');

-- 方剂明细
INSERT INTO tcm_formula_item (formula_id, herb_name, dosage, unit, sort_order) VALUES
-- 四君子汤
('formula-1', '党参', 15, 'g', 1),
('formula-1', '白术', 10, 'g', 2),
('formula-1', '茯苓', 15, 'g', 3),
('formula-1', '甘草',  6, 'g', 4),
-- 六味地黄丸
('formula-2', '熟地黄', 24, 'g', 1),
('formula-2', '山萸肉', 12, 'g', 2),
('formula-2', '山药',   12, 'g', 3),
('formula-2', '泽泻',    9, 'g', 4),
('formula-2', '茯苓',    9, 'g', 5),
('formula-2', '牡丹皮',  9, 'g', 6),
-- 逍遥散
('formula-3', '柴胡', 10, 'g', 1),
('formula-3', '白芍', 15, 'g', 2),
('formula-3', '当归', 10, 'g', 3),
('formula-3', '茯苓', 15, 'g', 4),
('formula-3', '白术', 10, 'g', 5),
('formula-3', '甘草',  6, 'g', 6),
('formula-3', '薄荷',  3, 'g', 7),
('formula-3', '生姜',  3, 'g', 8),
-- 补中益气汤
('formula-4', '黄芪', 30, 'g', 1),
('formula-4', '党参', 15, 'g', 2),
('formula-4', '白术', 10, 'g', 3),
('formula-4', '甘草',  6, 'g', 4),
('formula-4', '当归', 10, 'g', 5),
('formula-4', '陈皮',  6, 'g', 6),
('formula-4', '升麻',  6, 'g', 7),
('formula-4', '柴胡',  6, 'g', 8),
-- 八珍汤
('formula-5', '党参', 15, 'g', 1),
('formula-5', '白术', 10, 'g', 2),
('formula-5', '茯苓', 15, 'g', 3),
('formula-5', '甘草',  6, 'g', 4),
('formula-5', '当归', 10, 'g', 5),
('formula-5', '白芍', 10, 'g', 6),
('formula-5', '川芎',  8, 'g', 7),
('formula-5', '熟地黄', 15, 'g', 8),
-- 小柴胡汤
('formula-6', '柴胡',   24, 'g', 1),
('formula-6', '黄芩',    9, 'g', 2),
('formula-6', '党参',    9, 'g', 3),
('formula-6', '法半夏',  9, 'g', 4),
('formula-6', '甘草',    6, 'g', 5),
('formula-6', '生姜',    9, 'g', 6),
('formula-6', '大枣',   12, 'g', 7),
-- 桂枝汤
('formula-7', '桂枝',  9, 'g', 1),
('formula-7', '白芍',  9, 'g', 2),
('formula-7', '生姜',  9, 'g', 3),
('formula-7', '大枣', 12, 'g', 4),
('formula-7', '甘草',  6, 'g', 5),
-- 金匮肾气丸
('formula-8', '熟地黄', 24, 'g', 1),
('formula-8', '山萸肉', 12, 'g', 2),
('formula-8', '山药',   12, 'g', 3),
('formula-8', '泽泻',    9, 'g', 4),
('formula-8', '茯苓',    9, 'g', 5),
('formula-8', '牡丹皮',  9, 'g', 6),
('formula-8', '附子',    3, 'g', 7),
('formula-8', '桂枝',    3, 'g', 8);

-- ----------------------------
-- 7. 菜单权限
-- ----------------------------
INSERT INTO sys_menu VALUES (2008, '方剂管理', 2000, 8,  'formula',  'tcm/formula/index',  '', '', 1, 0, 'C', '0', '0', 'tcm:formula:list',  'documentation', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2009, '供应商管理', 2000, 9, 'supplier', 'tcm/supplier/index', '', '', 1, 0, 'C', '0', '0', 'tcm:supplier:list', 'international', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 方剂
INSERT INTO sys_menu VALUES (2810, '方剂查询', 2008, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2811, '方剂新增', 2008, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2812, '方剂修改', 2008, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2813, '方剂删除', 2008, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 供应商
INSERT INTO sys_menu VALUES (2820, '供应商查询', 2009, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2821, '供应商新增', 2009, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2822, '供应商修改', 2009, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2823, '供应商删除', 2009, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 角色-菜单关联 (admin 拥有全部)
INSERT INTO sys_role_menu VALUES (1,2008),(1,2009);
INSERT INTO sys_role_menu VALUES (1,2810),(1,2811),(1,2812),(1,2813);
INSERT INTO sys_role_menu VALUES (1,2820),(1,2821),(1,2822),(1,2823);

-- practitioner 可查看方剂和供应商
INSERT INTO sys_role_menu VALUES (100,2008),(100,2009);
INSERT INTO sys_role_menu VALUES (100,2810),(100,2820);

-- =============================================
-- TCM v4 升级脚本 - 针灸穴位管理 & 单位换算
-- 在执行 tcm_upgrade_v3 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 针灸穴位表
-- ----------------------------
DROP TABLE IF EXISTS tcm_acupoint;
CREATE TABLE tcm_acupoint (
  id              varchar(64)   NOT NULL                  COMMENT '穴位ID',
  name            varchar(100)  NOT NULL                  COMMENT '穴位名称',
  pinyin          varchar(100)  DEFAULT NULL              COMMENT '拼音',
  english_name    varchar(200)  DEFAULT NULL              COMMENT '英文名',
  meridian        varchar(100)  DEFAULT NULL              COMMENT '所属经络',
  location        varchar(500)  DEFAULT NULL              COMMENT '定位',
  indication      varchar(500)  DEFAULT NULL              COMMENT '主治',
  method          varchar(300)  DEFAULT NULL              COMMENT '刺法',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_acupoint_name (name),
  KEY idx_acupoint_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='针灸穴位表';

-- ----------------------------
-- 2. 单位换算表
-- ----------------------------
DROP TABLE IF EXISTS tcm_unit_conversion;
CREATE TABLE tcm_unit_conversion (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '主键',
  from_unit       varchar(20)   NOT NULL                  COMMENT '源单位',
  to_unit         varchar(20)   NOT NULL                  COMMENT '目标单位',
  factor          decimal(12,6) NOT NULL                  COMMENT '换算因子（1源=factor目标）',
  notes           varchar(200)  DEFAULT NULL              COMMENT '备注',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_unit_pair (from_unit, to_unit)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='单位换算表';

-- ----------------------------
-- 3. 种子数据 - 穴位
-- ----------------------------
INSERT INTO tcm_acupoint (id, name, pinyin, english_name, meridian, location, indication) VALUES
('acu-001', '百会', 'bǎi huì',   'GV20 Baihui',   '督脉',   '头顶正中线与两耳尖连线的交点', '头痛、眩晕、中风、失眠'),
('acu-002', '神庭', 'shén tíng',  'GV24 Shenting',  '督脉',   '前发际正中直上0.5寸', '头痛、眩晕、失眠、鼻渊'),
('acu-003', '印堂', 'yìn táng',   'EX-HN3 Yintang', '经外奇穴','两眉头连线的中点', '头痛、眩晕、鼻渊、失眠'),
('acu-004', '太阳', 'tài yáng',   'EX-HN5 Taiyang', '经外奇穴','眉梢与目外眦之间向后约1寸凹陷处', '头痛、目疾'),
('acu-005', '风池', 'fēng chí',   'GB20 Fengchi',    '足少阳胆经','枕骨之下，胸锁乳突肌与斜方肌上端之间的凹陷', '头痛、眩晕、颈项强痛、目赤肿痛'),
('acu-006', '风府', 'fēng fǔ',    'GV16 Fengfu',    '督脉',   '后发际正中直上1寸', '头痛、项强、眩晕、中风'),
('acu-007', '大椎', 'dà zhuī',    'GV14 Dazhui',    '督脉',   '第7颈椎棘突下凹陷中', '热病、疟疾、咳嗽、项强'),
('acu-008', '合谷', 'hé gǔ',      'LI4 Hegu',       '手阳明大肠经','第1、2掌骨间，第2掌骨桡侧中点', '头痛、目赤肿痛、牙痛、咽喉肿痛'),
('acu-009', '曲池', 'qū chí',     'LI11 Quchi',     '手阳明大肠经','肘横纹外侧端，屈肘时肱骨外上髁与肘横纹连线中点', '热病、咽喉肿痛、上肢不遂'),
('acu-010', '足三里', 'zú sān lǐ', 'ST36 Zusanli',   '足阳明胃经','犊鼻穴下3寸，胫骨前嵴外1横指', '胃痛、呕吐、腹胀、泄泻、虚劳'),
('acu-011', '三阴交', 'sān yīn jiāo','SP6 Sanyinjiao','足太阴脾经','内踝尖上3寸，胫骨内侧面后缘', '脾胃虚弱、月经不调、带下、遗精'),
('acu-012', '太冲', 'tài chōng',   'LR3 Taichong',   '足厥阴肝经','足背第1、2跖骨结合部之前凹陷中', '头痛、眩晕、目赤肿痛、胁痛'),
('acu-013', '内关', 'nèi guān',    'PC6 Neiguan',    '手厥阴心包经','腕横纹上2寸，掌长肌腱与桡侧腕屈肌腱之间', '心痛、心悸、胸闷、胃痛、呕吐'),
('acu-014', '涌泉', 'yǒng quán',   'KI1 Yongquan',   '足少阴肾经','足底前1/3与后2/3交界处凹陷中', '头痛、头顶痛、失眠、便秘'),
('acu-015', '气海', 'qì hǎi',     'CV6 Qihai',      '任脉',   '脐中下1.5寸', '虚脱、腹痛、泄泻、月经不调'),
('acu-016', '关元', 'guān yuán',   'CV4 Guanyuan',   '任脉',   '脐中下3寸', '中风脱证、虚劳、泄泻、痢疾'),
('acu-017', '中脘', 'zhōng wǎn',   'CV12 Zhongwan',  '任脉',   '脐中上4寸', '胃痛、呕吐、呃逆、腹胀'),
('acu-018', '天枢', 'tiān shū',    'ST25 Tianshu',   '足阳明胃经','脐中旁开2寸', '腹胀、泄泻、痢疾、便秘'),
('acu-019', '肩井', 'jiān jǐng',   'GB21 Jianjing',  '足少阳胆经','大椎与肩峰连线的中点', '肩背痹痛、上肢不遂、难产'),
('acu-020', '委中', 'wěi zhōng',   'BL40 Weizhong',  '足太阳膀胱经','腘横纹中点', '腰痛、下肢痿痹、腹痛、吐泻');

-- ----------------------------
-- 4. 种子数据 - 单位换算
-- ----------------------------
INSERT INTO tcm_unit_conversion (from_unit, to_unit, factor, notes) VALUES
('kg',  'g',    1000.000000, '千克 → 克'),
('g',   'kg',      0.001000, '克 → 千克'),
('g',   'mg',   1000.000000, '克 → 毫克'),
('mg',  'g',       0.001000, '毫克 → 克'),
('liang','g',     50.000000, '两 → 克（现代）'),
('g',   'liang',   0.020000, '克 → 两'),
('qian','g',       5.000000, '钱 → 克'),
('g',   'qian',    0.200000, '克 → 钱'),
('jin', 'g',     500.000000, '斤 → 克'),
('g',   'jin',     0.002000, '克 → 斤'),
('oz',  'g',      28.349523, '盎司 → 克'),
('g',   'oz',      0.035274, '克 → 盎司'),
('lb',  'g',     453.592370, '磅 → 克'),
('g',   'lb',      0.002205, '克 → 磅'),
('ml',  'g',       1.000000, '毫升 → 克（水近似）'),
('g',   'ml',      1.000000, '克 → 毫升（水近似）');

-- ----------------------------
-- 5. 菜单权限 - 穴位管理
-- ----------------------------
INSERT INTO sys_menu VALUES (2010, '穴位管理', 2000, 10, 'acupoint', 'tcm/acupoint/index', '', '', 1, 0, 'C', '0', '0', 'tcm:acupoint:list', 'guide', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2830, '穴位查询', 2010, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2831, '穴位新增', 2010, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2832, '穴位修改', 2010, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2833, '穴位删除', 2010, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT INTO sys_role_menu VALUES (1,2010),(1,2830),(1,2831),(1,2832),(1,2833);
INSERT INTO sys_role_menu VALUES (100,2010),(100,2830);

-- =============================================
-- TCM v5 升级脚本 - 中药材字典 & 经络字典 & 诊疗模板
-- 在执行 tcm_upgrade_v4 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 中药材字典表
-- ----------------------------
DROP TABLE IF EXISTS tcm_herb_dict;
CREATE TABLE tcm_herb_dict (
  id              varchar(64)   NOT NULL                  COMMENT '药材ID',
  name            varchar(100)  NOT NULL                  COMMENT '药材名称',
  alias           varchar(200)  DEFAULT NULL              COMMENT '别名',
  pinyin          varchar(100)  DEFAULT NULL              COMMENT '拼音',
  category        varchar(100)  DEFAULT NULL              COMMENT '分类',
  nature          varchar(50)   DEFAULT NULL              COMMENT '药性(寒/热/温/凉/平)',
  taste           varchar(100)  DEFAULT NULL              COMMENT '药味(辛/甘/苦/酸/咸/淡/涩)',
  toxicity        varchar(50)   DEFAULT NULL              COMMENT '毒性',
  meridian_tropism varchar(200) DEFAULT NULL              COMMENT '归经',
  efficacy        varchar(500)  DEFAULT NULL              COMMENT '功效',
  indication      varchar(500)  DEFAULT NULL              COMMENT '主治',
  dosage_range    varchar(100)  DEFAULT NULL              COMMENT '用量范围',
  contraindication varchar(500) DEFAULT NULL              COMMENT '禁忌',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_herb_name (name),
  KEY idx_herb_category (category),
  KEY idx_herb_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中药材字典表';

-- ----------------------------
-- 2. 经络字典表
-- ----------------------------
DROP TABLE IF EXISTS tcm_meridian;
CREATE TABLE tcm_meridian (
  id              varchar(64)   NOT NULL                  COMMENT '经络ID',
  name            varchar(100)  NOT NULL                  COMMENT '经络名称',
  english_name    varchar(200)  DEFAULT NULL              COMMENT '英文名',
  abbr            varchar(20)   DEFAULT NULL              COMMENT '缩写(如LU/LI/ST)',
  category        varchar(50)   DEFAULT NULL              COMMENT '分类(正经/奇经)',
  organ           varchar(100)  DEFAULT NULL              COMMENT '所属脏腑',
  pathway         varchar(1000) DEFAULT NULL              COMMENT '循行路线',
  acupoint_count  int(11)       DEFAULT 0                 COMMENT '穴位数量',
  indication      varchar(500)  DEFAULT NULL              COMMENT '主治概述',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_meridian_name (name),
  KEY idx_meridian_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经络字典表';

-- ----------------------------
-- 3. 诊疗模板表
-- ----------------------------
DROP TABLE IF EXISTS tcm_treatment_template;
CREATE TABLE tcm_treatment_template (
  id              varchar(64)   NOT NULL                  COMMENT '模板ID',
  name            varchar(100)  NOT NULL                  COMMENT '模板名称',
  disease         varchar(200)  DEFAULT NULL              COMMENT '病症名称',
  category        varchar(100)  DEFAULT NULL              COMMENT '分类',
  description     varchar(500)  DEFAULT NULL              COMMENT '模板说明',
  acupoints_json  longtext      DEFAULT NULL              COMMENT '推荐穴位(JSON)',
  formula_ids     varchar(500)  DEFAULT NULL              COMMENT '推荐方剂ID(JSON)',
  advice          varchar(500)  DEFAULT NULL              COMMENT '常用医嘱',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_template_name (name),
  KEY idx_template_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊疗模板表';

-- ----------------------------
-- 4. 种子数据 - 中药材字典 (80+ 常用药材)
-- ----------------------------
INSERT INTO tcm_herb_dict (id, name, alias, pinyin, category, nature, taste, meridian_tropism, efficacy, indication, dosage_range, contraindication) VALUES
-- === 解表药 ===
('herb-001','麻黄','龙沙、卑相','má huáng','解表药','温','辛、微苦','肺、膀胱','发汗解表，宣肺平喘，利水消肿','风寒感冒、咳嗽气喘、风水浮肿','2-10g','体虚多汗者忌用'),
('herb-002','桂枝','柳桂','guì zhī','解表药','温','辛、甘','心、肺、膀胱','发汗解肌，温通经脉，助阳化气','风寒感冒、寒凝血滞、痰饮蓄水','3-10g','温热病及阴虚火旺者忌用'),
('herb-003','紫苏叶','苏叶','zǐ sū yè','解表药','温','辛','肺、脾','解表散寒，行气和胃','风寒感冒、脾胃气滞','5-10g','气虚表虚者慎用'),
('herb-004','荆芥','假苏','jīng jiè','解表药','微温','辛','肺、肝','祛风解表，透疹消疮，止血','感冒头痛、麻疹不透、疮疡初起','5-10g','表虚自汗者忌用'),
('herb-005','防风','屏风','fáng fēng','解表药','微温','辛、甘','膀胱、肝、脾','祛风解表，胜湿止痛，止痉','感冒头痛、风湿痹痛、破伤风','5-10g','阴虚火旺者慎用'),
('herb-006','薄荷','银丹草','bò he','解表药','凉','辛','肺、肝','疏散风热，清利头目，利咽透疹','风热感冒、头痛目赤、咽喉肿痛','3-6g','体虚多汗者忌用'),
('herb-007','葛根','甘葛','gé gēn','解表药','凉','甘、辛','脾、胃','解肌退热，生津止渴，透疹升阳','外感发热、口渴、麻疹不透','10-15g','虚寒者慎用'),
('herb-008','柴胡','地薰','chái hú','解表药','微寒','辛、苦','肝、胆','和解表里，疏肝升阳','寒热往来、胸胁胀痛、月经不调','3-10g','肝阳上亢者慎用'),
('herb-009','菊花','甘菊','jú huā','解表药','微寒','辛、甘、苦','肺、肝','散风清热，平肝明目','风热感冒、头痛眩晕、目赤肿痛','5-10g','气虚胃寒者慎用'),
('herb-010','桑叶','霜桑叶','sāng yè','解表药','寒','甘、苦','肺、肝','疏散风热，清肺润燥，平肝明目','风热感冒、肺热咳嗽','5-10g','无特殊禁忌'),
-- === 清热药 ===
('herb-011','石膏','白虎','shí gāo','清热药','大寒','辛、甘','肺、胃','清热泻火，除烦止渴','高热烦渴、肺热喘咳','15-60g','脾胃虚寒者忌用'),
('herb-012','知母','蚳母','zhī mǔ','清热药','寒','苦、甘','肺、胃、肾','清热泻火，生津润燥','高热烦渴、内热消渴、肺热咳嗽','6-12g','脾虚便溏者忌用'),
('herb-013','黄芩','枯芩','huáng qín','清热药','寒','苦','肺、胆、脾、大肠、小肠','清热燥湿，泻火解毒，止血安胎','湿温暑湿、肺热咳嗽','3-10g','脾胃虚寒者忌用'),
('herb-014','黄连','川连','huáng lián','清热药','寒','苦','心、脾、胃、肝、胆、大肠','清热燥湿，泻火解毒','湿热泻痢、高热口渴、痈肿疔毒','2-5g','脾胃虚寒者忌用'),
('herb-015','黄柏','黄蘖','huáng bò','清热药','寒','苦','肾、膀胱','清热燥湿，泻火除蒸，解毒疗疮','湿热泻痢、骨蒸劳热','3-12g','脾虚泄泻者忌用'),
('herb-016','栀子','山栀','zhī zi','清热药','寒','苦','心、肺、三焦','泻火除烦，清热利湿，凉血解毒','热病心烦、湿热黄疸','3-10g','脾虚便溏者忌用'),
('herb-017','金银花','忍冬花','jīn yín huā','清热药','寒','甘','肺、心、胃','清热解毒，疏散风热','痈肿疔疮、外感风热、温病初起','6-15g','脾胃虚寒者慎用'),
('herb-018','连翘','黄花条','lián qiào','清热药','微寒','苦','肺、心、小肠','清热解毒，消痈散结，疏散风热','痈疽疮毒、风热感冒','6-15g','脾胃虚弱者慎用'),
('herb-019','蒲公英','黄花地丁','pú gōng yīng','清热药','寒','苦、甘','肝、胃','清热解毒，消肿散结，利尿通淋','乳痈肿痛、疔疮肿毒','10-30g','阳虚外寒者忌用'),
-- === 泻下药 ===
('herb-020','大黄','将军','dà huáng','泻下药','寒','苦','脾、胃、大肠、肝、心包','泻下攻积，清热泻火，凉血解毒','实热便秘、积滞腹痛','3-15g','孕妇忌用'),
-- === 祛风湿药 ===
('herb-021','独活','独摇草','dú huó','祛风湿药','微温','辛、苦','肾、膀胱','祛风除湿，通痹止痛','风寒湿痹、腰膝疼痛','3-10g','阴虚血燥者慎用'),
('herb-022','威灵仙','铁脚威灵仙','wēi líng xiān','祛风湿药','温','辛、咸','膀胱','祛风除湿，通络止痛','风湿痹痛、肢体麻木','6-10g','气血虚弱者慎用'),
-- === 化湿药 ===
('herb-023','藿香','土藿香','huò xiāng','化湿药','微温','辛','脾、胃、肺','芳香化湿，和中止呕，发表解暑','湿滞中焦、暑湿感冒','5-10g','阴虚火旺者慎用'),
('herb-024','苍术','赤术','cāng zhú','化湿药','温','辛、苦','脾、胃','燥湿健脾，祛风散寒','湿阻中焦、风寒感冒','5-10g','阴虚内热者忌用'),
-- === 利水渗湿药 ===
('herb-025','茯苓','云苓','fú líng','利水渗湿药','平','甘、淡','心、肺、脾、肾','利水渗湿，健脾宁心','水肿尿少、脾虚泄泻、心悸失眠','10-15g','虚寒滑精者慎用'),
('herb-026','泽泻','水泻','zé xiè','利水渗湿药','寒','甘、淡','肾、膀胱','利水渗湿，泄热','小便不利、水肿胀满','6-10g','肾虚精滑者忌用'),
('herb-027','薏苡仁','薏米','yì yǐ rén','利水渗湿药','微寒','甘、淡','脾、胃、肺','利水渗湿，健脾止泻，清热排脓','水肿、脾虚泄泻','10-30g','孕妇慎用'),
('herb-028','车前子','车轮菜子','chē qián zǐ','利水渗湿药','寒','甘','肝、肾、肺、小肠','清热利尿，渗湿止泻，明目祛痰','热淋涩痛、水肿胀满','5-15g','肾虚滑精者慎用'),
-- === 温里药 ===
('herb-029','附子','乌头','fù zǐ','温里药','大热','辛、甘','心、肾、脾','回阳救逆，补火助阳，散寒止痛','亡阳证、阳虚证、寒湿痹痛','3-15g','阴虚阳亢者忌用；孕妇忌用'),
('herb-030','干姜','白姜','gān jiāng','温里药','热','辛','脾、胃、肾、心、肺','温中散寒，回阳通脉，燥湿消痰','脘腹冷痛、亡阳证','3-10g','热证及阴虚内热者忌用'),
('herb-031','肉桂','桂皮','ròu guì','温里药','大热','辛、甘','肾、脾、心、肝','补火助阳，散寒止痛，温通经脉','阳痿宫冷、腰膝冷痛','1-5g','阴虚火旺者忌用；孕妇忌用'),
-- === 理气药 ===
('herb-032','陈皮','橘皮','chén pí','理气药','温','辛、苦','脾、肺','理气健脾，燥湿化痰','脘腹胀满、食少吐泻、咳嗽痰多','3-10g','气虚体燥者慎用'),
('herb-033','枳壳','枳实壳','zhǐ ké','理气药','微寒','苦、辛、酸','脾、胃','理气宽中，行滞消胀','胸胁气滞、胀满疼痛','3-10g','孕妇慎用'),
('herb-034','香附','莎草根','xiāng fù','理气药','平','辛、微苦、微甘','肝、脾、三焦','疏肝解郁，理气宽中，调经止痛','肝郁气滞、月经不调','6-10g','气虚无滞者慎用'),
('herb-035','木香','广木香','mù xiāng','理气药','温','辛、苦','脾、胃、大肠、三焦、胆','行气止痛，健脾消食','脘腹胀痛、泻痢后重','3-6g','阴虚津亏者慎用'),
-- === 消食药 ===
('herb-036','山楂','红果','shān zhā','消食药','微温','酸、甘','脾、胃、肝','消食健胃，行气散瘀','肉食积滞、胃脘胀满','10-15g','脾胃虚弱者慎用'),
('herb-037','神曲','六神曲','shén qū','消食药','温','辛、甘','脾、胃','消食和胃','饮食停滞、消化不良','6-15g','无特殊禁忌'),
-- === 止血药 ===
('herb-038','三七','田七','sān qī','止血药','温','甘、微苦','肝、胃','散瘀止血，消肿定痛','各种出血证、跌打损伤','3-10g','孕妇忌用'),
('herb-039','白及','白根','bái jí','止血药','微寒','苦、甘、涩','肺、肝、胃','收敛止血，消肿生肌','咯血吐血、外伤出血','6-15g','不宜与乌头同用'),
-- === 活血化瘀药 ===
('herb-040','川芎','芎藭','chuān xiōng','活血化瘀药','温','辛','肝、胆、心包','活血行气，祛风止痛','胸痹心痛、头痛、风湿痹痛','3-10g','阴虚火旺者慎用'),
('herb-041','丹参','紫丹参','dān shēn','活血化瘀药','微寒','苦','心、肝','活血祛瘀，通经止痛，清心除烦','月经不调、胸腹刺痛、心烦不眠','10-15g','孕妇慎用'),
('herb-042','桃仁','桃核仁','táo rén','活血化瘀药','平','苦、甘','心、肝、大肠','活血祛瘀，润肠通便','瘀血阻滞、肠燥便秘','5-10g','孕妇忌用'),
('herb-043','红花','草红花','hóng huā','活血化瘀药','温','辛','心、肝','活血通经，散瘀止痛','痛经闭经、跌打损伤','3-10g','孕妇忌用'),
-- === 化痰止咳平喘药 ===
('herb-044','法半夏','制半夏','fǎ bàn xià','化痰药','温','辛','脾、胃、肺','燥湿化痰，降逆止呕，消痞散结','痰多咳喘、痰饮眩悸','3-10g','阴虚燥咳者忌用'),
('herb-045','桔梗','苦桔梗','jié gěng','化痰药','平','辛、苦','肺','宣肺祛痰，利咽排脓','咳嗽痰多、咽喉肿痛','3-10g','阴虚久咳者慎用'),
('herb-046','杏仁','苦杏仁','xìng rén','止咳平喘药','微温','苦','肺、大肠','降气止咳平喘，润肠通便','咳嗽气喘、肠燥便秘','5-10g','阴虚咳嗽者慎用'),
('herb-047','贝母','川贝母','bèi mǔ','化痰药','微寒','苦、甘','肺、心','清热润肺，化痰止咳','肺热燥咳、痰黄咯痰','3-10g','脾胃虚寒者慎用'),
-- === 安神药 ===
('herb-048','酸枣仁','枣仁','suān zǎo rén','安神药','平','甘、酸','肝、胆、心','养心补肝，宁心安神','虚烦不眠、惊悸多梦','10-15g','有实邪郁火者慎用'),
('herb-049','远志','小草','yuǎn zhì','安神药','微温','苦、辛','心、肾、肺','安神益智，祛痰开窍，消散痈肿','心肾不交、失眠多梦','3-10g','胃炎胃溃疡者慎用'),
-- === 平肝熄风药 ===
('herb-050','天麻','赤箭','tiān má','平肝熄风药','平','甘','肝','息风止痉，平抑肝阳，祛风通络','头痛眩晕、肢体麻木、小儿惊风','3-10g','气血虚甚者慎用'),
('herb-051','钩藤','双钩藤','gōu téng','平肝熄风药','微寒','甘','肝、心包','息风定惊，清热平肝','头痛眩晕、惊痫抽搐','3-12g','无实热者慎用'),
-- === 补气药 ===
('herb-052','人参','白参','rén shēn','补气药','微温','甘、微苦','脾、肺、心、肾','大补元气，复脉固脱，补脾益肺','体虚欲脱、脾虚食少、气短乏力','3-10g','实证热证者忌用'),
('herb-053','黄芪','绵芪','huáng qí','补气药','微温','甘','肺、脾','补气固表，利尿托毒，排脓敛疮','气虚乏力、自汗、水肿','10-30g','实证热证者忌用'),
('herb-054','党参','上党参','dǎng shēn','补气药','平','甘','脾、肺','补中益气，健脾益肺','脾肺虚弱、气短心悸','10-30g','实证热证者慎用'),
('herb-055','白术','于术','bái zhú','补气药','温','苦、甘','脾、胃','健脾益气，燥湿利水，止汗安胎','脾虚食少、腹胀泄泻','6-12g','阴虚内热者慎用'),
('herb-056','山药','薯蓣','shān yào','补气药','平','甘','脾、肺、肾','补脾养胃，生津益肺，补肾涩精','脾虚泄泻、肺虚咳喘','15-30g','湿盛中满者忌用'),
('herb-057','甘草','国老','gān cǎo','补气药','平','甘','心、肺、脾、胃','补脾益气，清热解毒，调和诸药','脾胃虚弱、咳嗽痰多','2-10g','湿盛胀满者忌用'),
-- === 补阳药 ===
('herb-058','鹿茸','斑龙珠','lù róng','补阳药','温','甘、咸','肾、肝','壮肾阳，益精血，强筋骨','肾阳虚衰、精血不足','1-2g','阴虚火旺者忌用'),
('herb-059','杜仲','思仙','dù zhòng','补阳药','温','甘','肝、肾','补肝肾，强筋骨，安胎','腰膝酸痛、筋骨无力','6-10g','阴虚火旺者慎用'),
('herb-060','肉苁蓉','大芸','ròu cōng róng','补阳药','温','甘、咸','肾、大肠','补肾阳，益精血，润肠通便','肾阳虚、精血不足','10-20g','阴虚火旺者忌用'),
-- === 补血药 ===
('herb-061','当归','秦归','dāng guī','补血药','温','甘、辛','肝、心、脾','补血活血，调经止痛，润肠通便','血虚萎黄、月经不调','6-12g','湿盛中满者慎用'),
('herb-062','熟地黄','熟地','shú dì huáng','补血药','微温','甘','肝、肾','补血滋阴，益精填髓','血虚萎黄、头晕目眩','9-15g','脾虚食少者慎用'),
('herb-063','白芍','杭芍','bái sháo','补血药','微寒','苦、酸','肝、脾','养血调经，柔肝止痛，平抑肝阳','血虚萎黄、月经不调、肝脾不和','5-10g','阳衰虚寒者不宜单用'),
('herb-064','阿胶','驴皮胶','ē jiāo','补血药','平','甘','肺、肝、肾','补血滋阴，润燥止血','血虚萎黄、眩晕心悸','3-10g','脾虚便溏者慎用'),
('herb-065','何首乌','制首乌','hé shǒu wū','补血药','微温','苦、甘、涩','肝、心、肾','补肝肾，益精血，乌须发','血虚萎黄、腰膝酸软','10-30g','大便溏泄者忌用'),
-- === 补阴药 ===
('herb-066','沙参','北沙参','shā shēn','补阴药','微寒','甘','肺、胃','养阴清肺，益胃生津','肺热燥咳、阴虚劳嗽','10-15g','风寒咳嗽者忌用'),
('herb-067','麦冬','麦门冬','mài dōng','补阴药','微寒','甘、微苦','心、肺、胃','养阴生津，润肺清心','肺燥干咳、津伤口渴','6-12g','脾胃虚寒者慎用'),
('herb-068','枸杞子','枸杞','gǒu qǐ zǐ','补阴药','平','甘','肝、肾','滋补肝肾，益精明目','虚劳精亏、腰膝酸痛','6-12g','脾虚便溏者慎用'),
('herb-069','女贞子','冬青子','nǚ zhēn zǐ','补阴药','凉','甘、苦','肝、肾','滋补肝肾，明目乌发','肝肾阴虚、头晕目眩','6-12g','脾胃虚寒者慎用'),
('herb-070','山萸肉','山茱萸','shān zhū yú','补阴药','微温','酸、涩','肝、肾','补益肝肾，收涩固脱','眩晕耳鸣、腰膝酸痛','6-12g','素有湿热者不宜'),
('herb-071','石斛','铁皮石斛','shí hú','补阴药','微寒','甘','胃、肾','益胃生津，滋阴清热','热病津伤、口干烦渴','6-12g','湿温未化者忌用'),
-- === 收涩药 ===
('herb-072','五味子','北五味','wǔ wèi zǐ','收涩药','温','酸、甘','肺、心、肾','收敛固涩，益气生津，补肾宁心','久咳虚喘、自汗盗汗','2-6g','外有表邪内有实热者忌用'),
('herb-073','莲子','莲实','lián zǐ','收涩药','平','甘、涩','脾、肾、心','补脾止泻，益肾涩精，养心安神','脾虚泄泻、遗精滑精','6-15g','中满痞胀者忌用'),
-- === 驱虫药 ===
('herb-074','使君子','留求子','shǐ jūn zǐ','驱虫药','温','甘','脾、胃','杀虫消积','蛔虫病、蛲虫病','6-10g','不可与热茶同服'),
-- === 外用药 ===
('herb-075','冰片','龙脑','bīng piàn','开窍药','微寒','辛、苦','心、脾、肺','开窍醒神，清热止痛','中风痰厥、目赤肿痛','0.15-0.3g','孕妇忌用'),
-- === 常用配伍药 ===
('herb-076','生姜','鲜姜','shēng jiāng','解表药','微温','辛','肺、脾、胃','解表散寒，温中止呕，化痰止咳','风寒感冒、胃寒呕吐','3-10g','阴虚内热者忌用'),
('herb-077','大枣','红枣','dà zǎo','补气药','温','甘','脾、胃、心','补中益气，养血安神','脾虚食少、倦怠乏力、气血不足','6-15g','湿盛中满者慎用'),
('herb-078','升麻','周升麻','shēng má','解表药','微寒','辛、微甘','肺、脾、胃、大肠','升阳举陷，清热解毒','气虚下陷、中气不足','3-10g','阴虚火旺者慎用'),
('herb-079','牡丹皮','丹皮','mǔ dān pí','清热药','微寒','苦、辛','心、肝、肾','清热凉血，活血化瘀','温毒发斑、血滞经闭','6-12g','孕妇慎用'),
('herb-080','地黄','生地','dì huáng','清热药','寒','甘、苦','心、肝、肾','清热凉血，养阴生津','热病伤阴、发斑发疹','10-15g','脾虚湿滞者慎用');

-- ----------------------------
-- 5. 种子数据 - 经络 (14条)
-- ----------------------------
INSERT INTO tcm_meridian (id, name, english_name, abbr, category, organ, pathway, acupoint_count, indication) VALUES
('mer-01','手太阴肺经','Lung Meridian','LU','正经','肺','起于中焦，下络大肠，还循胃口，上膈属肺，从肺系横出腋下，沿上肢内侧前缘下行至拇指端',11,'咳嗽、气喘、咽喉肿痛、胸部满闷'),
('mer-02','手阳明大肠经','Large Intestine Meridian','LI','正经','大肠','起于食指末端，沿上肢外侧前缘上行至肩，经颈部至面部，止于对侧鼻翼旁',20,'齿痛、咽喉肿痛、鼻衄、口干'),
('mer-03','足阳明胃经','Stomach Meridian','ST','正经','胃','起于鼻翼两侧，沿面部、颈部下行经胸腹，沿下肢外侧前缘至足第2趾端',45,'胃痛、腹胀、呕吐、咽喉肿痛'),
('mer-04','足太阴脾经','Spleen Meridian','SP','正经','脾','起于足大趾末端，沿下肢内侧上行经腹部，止于腋下',21,'胃脘痛、食则呕、腹胀便溏、月经不调'),
('mer-05','手少阴心经','Heart Meridian','HT','正经','心','起于心中，出属心系，下膈络小肠，其支者从心系上挟咽喉，其直者从心系上肺，出腋下',9,'心痛、心悸、失眠、咽干口渴'),
('mer-06','手太阳小肠经','Small Intestine Meridian','SI','正经','小肠','起于小指外侧端，沿上肢外侧后缘上行至肩胛，经颈部上达面部至耳前',19,'咽喉肿痛、颊肿、耳聋、目黄'),
('mer-07','足太阳膀胱经','Bladder Meridian','BL','正经','膀胱','起于目内眦，上行额部，交于头顶，沿后背下行至腰骶，经下肢后侧至小趾端',67,'头痛、目痛、项背腰臀痛、小便不利'),
('mer-08','足少阴肾经','Kidney Meridian','KI','正经','肾','起于小趾下面，斜向足心涌泉穴，沿下肢内侧后缘上行，经腹部至胸部',27,'咳血、气喘、舌干咽喉肿痛、腰脊痛'),
('mer-09','手厥阴心包经','Pericardium Meridian','PC','正经','心包','起于胸中，出属心包络，下膈历络三焦，沿上肢内侧正中下行至中指端',9,'心痛、心悸、胸闷、癫狂'),
('mer-10','手少阳三焦经','Triple Energizer Meridian','TE','正经','三焦','起于无名指末端，沿上肢外侧正中上行至肩，经颈部至耳后，止于眉梢',23,'腹胀、水肿、遗尿、耳聋、咽喉肿痛'),
('mer-11','足少阳胆经','Gallbladder Meridian','GB','正经','胆','起于目外眦，经头部侧面下行至肩，沿体侧下行经下肢外侧至足第4趾端',44,'口苦、目眩、头痛、耳聋、胁痛'),
('mer-12','足厥阴肝经','Liver Meridian','LR','正经','肝','起于足大趾背毫毛处，沿下肢内侧上行经阴部、腹部，止于肝，上注于肺',14,'腰痛、胸满、呃逆、遗尿、疝气'),
('mer-13','督脉','Governor Vessel','GV','奇经','脑、脊髓','起于胞中，下出会阴，沿脊柱后面上行至项部，入脑，上达头顶，沿前额正中线至鼻柱',28,'脊强反折、头痛、癫狂、中风'),
('mer-14','任脉','Conception Vessel','CV','奇经','胞宫','起于胞中，下出会阴，沿腹面正中线上行至咽喉，上至下颌，环绕口唇',24,'疝气、带下、腹中结块、月经不调');

-- ----------------------------
-- 6. 种子数据 - 诊疗模板 (10个常见病症)
-- ----------------------------
INSERT INTO tcm_treatment_template (id, name, disease, category, description, acupoints_json, formula_ids, advice) VALUES
('tmpl-01','失眠标准方案','失眠症','内科','适用于心脾两虚型失眠','["百会","神门","三阴交","安眠","内关","足三里"]','["formula-5"]','忌浓茶咖啡，睡前泡脚，规律作息'),
('tmpl-02','颈椎病方案','颈椎病','骨伤科','适用于颈型和神经根型颈椎病','["风池","天柱","大椎","后溪","肩井","合谷","外关"]','[]','注意颈部保暖，避免长时间低头，适当做颈部操'),
('tmpl-03','慢性胃炎方案','慢性胃炎','内科','适用于脾胃虚寒型胃炎','["中脘","足三里","天枢","脾俞","胃俞","内关"]','["formula-1"]','饮食清淡，忌辛辣生冷，定时定量进食'),
('tmpl-04','月经不调方案','月经不调','妇科','适用于气滞血瘀型月经不调','["关元","气海","三阴交","太冲","血海","合谷"]','["formula-3"]','经期注意保暖，避免剧烈运动，保持心情舒畅'),
('tmpl-05','头痛方案','头痛','内科','适用于各类头痛','["百会","太阳","风池","合谷","太冲","印堂"]','["formula-6"]','注意休息，避免风寒，保持充足睡眠'),
('tmpl-06','腰痛方案','腰痛','骨伤科','适用于寒湿腰痛和肾虚腰痛','["肾俞","命门","委中","腰阳关","大肠俞","环跳"]','["formula-8"]','避免久坐久站，注意腰部保暖，适当腰背肌锻炼'),
('tmpl-07','感冒方案','感冒','内科','适用于风寒感冒','["风池","大椎","合谷","列缺","迎香","风府"]','["formula-7"]','多饮温水，注意保暖休息，清淡饮食'),
('tmpl-08','高血压方案','高血压','内科','适用于肝阳上亢型高血压','["太冲","太溪","曲池","百会","风池","足三里"]','["formula-2"]','低盐饮食，避免情绪激动，监测血压，适当运动'),
('tmpl-09','便秘方案','便秘','内科','适用于气虚便秘和阴虚便秘','["天枢","大肠俞","支沟","上巨虚","足三里","照海"]','["formula-4"]','多食粗纤维食物，适量饮水，养成定时排便习惯'),
('tmpl-10','肩周炎方案','肩周炎','骨伤科','适用于肩关节周围炎','["肩髃","肩髎","肩贞","曲池","合谷","外关","条口"]','[]','坚持肩关节功能锻炼，注意保暖，避免过度劳累');

-- ----------------------------
-- 7. 菜单权限
-- ----------------------------
INSERT INTO sys_menu VALUES (2011, '草药字典', 2000, 11, 'herb-dict', 'tcm/herb-dict/index', '', '', 1, 0, 'C', '0', '0', 'tcm:herbdict:list', 'component', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2012, '经络字典', 2000, 12, 'meridian', 'tcm/meridian/index', '', '', 1, 0, 'C', '0', '0', 'tcm:meridian:list', 'guide', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2013, '诊疗模板', 2000, 13, 'template', 'tcm/template/index', '', '', 1, 0, 'C', '0', '0', 'tcm:template:list', 'clipboard', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 草药字典
INSERT INTO sys_menu VALUES (2840, '草药查询', 2011, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:herbdict:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2841, '草药新增', 2011, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:herbdict:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2842, '草药修改', 2011, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:herbdict:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2843, '草药删除', 2011, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:herbdict:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 经络字典
INSERT INTO sys_menu VALUES (2850, '经络查询', 2012, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:meridian:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2851, '经络新增', 2012, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:meridian:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2852, '经络修改', 2012, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:meridian:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2853, '经络删除', 2012, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:meridian:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 诊疗模板
INSERT INTO sys_menu VALUES (2860, '模板查询', 2013, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:template:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2861, '模板新增', 2013, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:template:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2862, '模板修改', 2013, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:template:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2863, '模板删除', 2013, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:template:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 角色-菜单关联
INSERT INTO sys_role_menu VALUES (1,2011),(1,2012),(1,2013);
INSERT INTO sys_role_menu VALUES (1,2840),(1,2841),(1,2842),(1,2843);
INSERT INTO sys_role_menu VALUES (1,2850),(1,2851),(1,2852),(1,2853);
INSERT INTO sys_role_menu VALUES (1,2860),(1,2861),(1,2862),(1,2863);
INSERT INTO sys_role_menu VALUES (100,2011),(100,2012),(100,2013);
INSERT INTO sys_role_menu VALUES (100,2840),(100,2850),(100,2860);

-- =============================================
-- TCM v5 种子数据补充 - 穴位扩充到100+ & 方剂扩充到50+
-- 在执行 tcm_upgrade_v5_herb_meridian_template.sql 之后执行
-- =============================================

-- ----------------------------
-- 1. 扩充穴位 (补充80个,加上原有20个=100个)
-- ----------------------------
INSERT IGNORE INTO tcm_acupoint (id, name, pinyin, english_name, meridian, location, indication) VALUES
-- 手太阴肺经
('acu-021','中府','zhōng fǔ','LU1 Zhongfu','手太阴肺经','胸前壁外上方，锁骨下窝外侧，前正中线旁开6寸','咳嗽、气喘、胸满痛'),
('acu-022','尺泽','chǐ zé','LU5 Chize','手太阴肺经','肘横纹中，肱二头肌腱桡侧凹陷处','咳嗽、气喘、咽喉肿痛'),
('acu-023','列缺','liè quē','LU7 Lieque','手太阴肺经','桡骨茎突上方，腕横纹上1.5寸','头痛项强、咳嗽气喘'),
('acu-024','太渊','tài yuān','LU9 Taiyuan','手太阴肺经','腕掌侧横纹桡侧端，桡动脉搏动处','咳嗽气喘、胸痛'),
('acu-025','少商','shào shāng','LU11 Shaoshang','手太阴肺经','拇指末节桡侧，距指甲角0.1寸','咽喉肿痛、中风昏迷'),
-- 手阳明大肠经
('acu-026','商阳','shāng yáng','LI1 Shangyang','手阳明大肠经','食指末节桡侧，距指甲角0.1寸','齿痛、咽喉肿痛、中风昏迷'),
('acu-027','三间','sān jiān','LI3 Sanjian','手阳明大肠经','第2掌指关节桡侧后方凹陷处','齿痛、咽喉肿痛'),
('acu-028','手三里','shǒu sān lǐ','LI10 Shousanli','手阳明大肠经','曲池穴下2寸处','上肢不遂、腹痛腹泻'),
('acu-029','迎香','yíng xiāng','LI20 Yingxiang','手阳明大肠经','鼻翼外缘中点旁，鼻唇沟中','鼻塞不通、口歪面痒'),
-- 足阳明胃经
('acu-030','四白','sì bái','ST2 Sibai','足阳明胃经','目正视时瞳孔直下，眶下孔凹陷处','目赤痛痒、面痛口歪'),
('acu-031','地仓','dì cāng','ST4 Dicang','足阳明胃经','口角外侧，上直对瞳孔','口角歪斜、流涎'),
('acu-032','颊车','jiá chē','ST6 Jiache','足阳明胃经','下颌角前上方约1横指','齿痛、面痛口歪'),
('acu-033','下关','xià guān','ST7 Xiaguan','足阳明胃经','颧弓下缘中央与下颌切迹之间凹陷处','齿痛、面痛、耳聋'),
('acu-034','头维','tóu wéi','ST8 Touwei','足阳明胃经','额角发际上0.5寸','头痛目眩'),
('acu-035','梁丘','liáng qiū','ST34 Liangqiu','足阳明胃经','髌底上2寸，股外侧肌与股直肌腱之间','胃痛、膝肿痛'),
('acu-036','犊鼻','dú bí','ST35 Dubi','足阳明胃经','屈膝，髌韧带外侧凹陷中','膝痛、下肢不遂'),
('acu-037','丰隆','fēng lóng','ST40 Fenglong','足阳明胃经','外踝尖上8寸，条口外1寸','头痛眩晕、咳嗽痰多'),
('acu-038','内庭','nèi tíng','ST44 Neiting','足阳明胃经','足背第2、3趾间缝纹端','齿痛、口歪、胃痛吐酸'),
-- 足太阴脾经
('acu-039','公孙','gōng sūn','SP4 Gongsun','足太阴脾经','第1跖骨基底部前下方赤白肉际处','胃痛呕吐、腹痛泄泻'),
('acu-040','阴陵泉','yīn líng quán','SP9 Yinlingquan','足太阴脾经','胫骨内侧踝后下方凹陷处','腹胀、水肿、小便不利'),
('acu-041','血海','xuè hǎi','SP10 Xuehai','足太阴脾经','髌底内侧端上2寸','月经不调、瘾疹湿疹'),
('acu-042','大包','dà bāo','SP21 Dabao','足太阴脾经','腋中线上，第6肋间隙处','气喘、全身疼痛'),
-- 手少阴心经
('acu-043','少海','shào hǎi','HT3 Shaohai','手少阴心经','屈肘，肘横纹内侧端与肱骨内上髁之间','心痛、肘臂疼痛'),
('acu-044','通里','tōng lǐ','HT5 Tongli','手少阴心经','腕横纹上1寸，尺侧腕屈肌腱桡侧缘','心悸、舌强不语'),
('acu-045','神门','shén mén','HT7 Shenmen','手少阴心经','腕横纹尺侧端，尺侧腕屈肌腱桡侧凹陷处','心痛心烦、惊悸失眠'),
-- 手太阳小肠经
('acu-046','后溪','hòu xī','SI3 Houxi','手太阳小肠经','第5掌指关节后尺侧近端赤白肉际凹陷中','头项强痛、目赤、耳聋'),
('acu-047','养老','yǎng lǎo','SI6 Yanglao','手太阳小肠经','尺骨茎突桡侧骨缝凹陷中','目视不明、肩臂酸痛'),
('acu-048','听宫','tīng gōng','SI19 Tinggong','手太阳小肠经','耳屏前，下颌骨髁突后方，张口时凹陷处','耳鸣耳聋、齿痛'),
-- 足太阳膀胱经
('acu-049','攒竹','cuán zhú','BL2 Cuanzhu','足太阳膀胱经','眉头凹陷中，眶上切迹处','头痛、目视不明、流泪'),
('acu-050','天柱','tiān zhù','BL10 Tianzhu','足太阳膀胱经','后发际正中直上0.5寸，旁开1.3寸','头痛项强、鼻塞目眩'),
('acu-051','风门','fēng mén','BL12 Fengmen','足太阳膀胱经','第2胸椎棘突下，旁开1.5寸','伤风咳嗽、头痛项强'),
('acu-052','肺俞','fèi shū','BL13 Feishu','足太阳膀胱经','第3胸椎棘突下，旁开1.5寸','咳嗽气喘、骨蒸潮热'),
('acu-053','心俞','xīn shū','BL15 Xinshu','足太阳膀胱经','第5胸椎棘突下，旁开1.5寸','心痛心悸、失眠健忘'),
('acu-054','膈俞','gé shū','BL17 Geshu','足太阳膀胱经','第7胸椎棘突下，旁开1.5寸','呕吐呃逆、各种血证'),
('acu-055','肝俞','gān shū','BL18 Ganshu','足太阳膀胱经','第9胸椎棘突下，旁开1.5寸','黄疸胁痛、目赤目眩'),
('acu-056','胆俞','dǎn shū','BL19 Danshu','足太阳膀胱经','第10胸椎棘突下，旁开1.5寸','黄疸口苦、胁痛胸满'),
('acu-057','脾俞','pí shū','BL20 Pishu','足太阳膀胱经','第11胸椎棘突下，旁开1.5寸','腹胀泄泻、呕吐纳呆'),
('acu-058','胃俞','wèi shū','BL21 Weishu','足太阳膀胱经','第12胸椎棘突下，旁开1.5寸','胃脘痛、呕吐、腹胀'),
('acu-059','肾俞','shèn shū','BL23 Shenshu','足太阳膀胱经','第2腰椎棘突下，旁开1.5寸','腰痛、遗尿遗精、耳鸣耳聋'),
('acu-060','大肠俞','dà cháng shū','BL25 Dachangshu','足太阳膀胱经','第4腰椎棘突下，旁开1.5寸','腹胀腹痛、泄泻便秘、腰痛'),
('acu-061','膀胱俞','páng guāng shū','BL28 Pangguangshu','足太阳膀胱经','第2骶椎棘突下，旁开1.5寸','小便不利、腰脊强痛'),
('acu-062','承山','chéng shān','BL57 Chengshan','足太阳膀胱经','小腿后面正中，伸直小腿或足跟上提时腓肠肌肌腹下出现尖角凹陷处','腰腿拘急疼痛、痔疾'),
('acu-063','昆仑','kūn lún','BL60 Kunlun','足太阳膀胱经','外踝尖与跟腱之间凹陷处','头痛项强、腰痛、足踝肿痛'),
('acu-064','至阴','zhì yīn','BL67 Zhiyin','足太阳膀胱经','小趾末节外侧，距趾甲角0.1寸','头痛目痛、鼻塞、胎位不正'),
-- 足少阴肾经
('acu-065','太溪','tài xī','KI3 Taixi','足少阴肾经','内踝尖与跟腱之间凹陷处','头痛目眩、咽喉肿痛、齿痛、耳聋'),
('acu-066','照海','zhào hǎi','KI6 Zhaohai','足少阴肾经','内踝尖下方凹陷处','失眠、咽喉干痛、月经不调'),
('acu-067','复溜','fù liū','KI7 Fuliu','足少阴肾经','太溪穴上2寸，跟腱前缘','水肿、盗汗自汗、腹胀泄泻'),
-- 手厥阴心包经
('acu-068','曲泽','qū zé','PC3 Quze','手厥阴心包经','肘横纹中，肱二头肌腱尺侧缘凹陷处','心痛心悸、胃痛呕吐'),
('acu-069','大陵','dà líng','PC7 Daling','手厥阴心包经','腕横纹中央，掌长肌腱与桡侧腕屈肌腱之间','心痛心悸、胃痛呕吐'),
('acu-070','劳宫','láo gōng','PC8 Laogong','手厥阴心包经','掌心第2、3掌骨之间偏于第3掌骨中','中风昏迷、口疮、心痛'),
('acu-071','中冲','zhōng chōng','PC9 Zhongchong','手厥阴心包经','中指末端尖处','中风昏迷、舌强不语、中暑'),
-- 手少阳三焦经
('acu-072','外关','wài guān','TE5 Waiguan','手少阳三焦经','腕背横纹上2寸，桡骨与尺骨之间','热病、头痛、耳聋耳鸣'),
('acu-073','支沟','zhī gōu','TE6 Zhigou','手少阳三焦经','腕背横纹上3寸，桡骨与尺骨之间','便秘、胁肋痛、耳聋耳鸣'),
('acu-074','翳风','yì fēng','TE17 Yifeng','手少阳三焦经','耳垂后方，乳突与下颌角之间凹陷处','耳鸣耳聋、口眼歪斜'),
('acu-075','丝竹空','sī zhú kōng','TE23 Sizhukong','手少阳三焦经','眉梢凹陷处','头痛目眩、目赤肿痛'),
-- 足少阳胆经
('acu-076','瞳子髎','tóng zǐ liáo','GB1 Tongziliao','足少阳胆经','目外眦旁0.5寸','头痛目赤、目痛流泪'),
('acu-077','率谷','shuài gǔ','GB8 Shuaigu','足少阳胆经','耳尖直上入发际1.5寸','头痛眩晕、呕吐'),
('acu-078','阳陵泉','yáng líng quán','GB34 Yanglingquan','足少阳胆经','腓骨小头前下方凹陷处','胁痛口苦、半身不遂、下肢痿痹'),
('acu-079','悬钟','xuán zhōng','GB39 Xuanzhong','足少阳胆经','外踝尖上3寸','颈项强痛、胸腹胀满、下肢痿痹'),
('acu-080','环跳','huán tiào','GB30 Huantiao','足少阳胆经','股骨大转子最高点与骶管裂孔连线外1/3处','腰腿痛、下肢痿痹、半身不遂'),
-- 足厥阴肝经
('acu-081','行间','xíng jiān','LR2 Xingjian','足厥阴肝经','足背第1、2趾间缝纹端','头痛目赤、失眠、月经不调'),
('acu-082','期门','qī mén','LR14 Qimen','足厥阴肝经','乳头直下，第6肋间隙，前正中线旁开4寸','胸胁胀满疼痛、呕吐吞酸'),
-- 督脉补充
('acu-083','腰阳关','yāo yáng guān','GV3 Yaoyangguan','督脉','第4腰椎棘突下凹陷中','腰骶疼痛、月经不调、遗精阳痿'),
('acu-084','命门','mìng mén','GV4 Mingmen','督脉','第2腰椎棘突下凹陷中','腰脊强痛、遗精阳痿、带下'),
('acu-085','身柱','shēn zhù','GV12 Shenzhu','督脉','第3胸椎棘突下凹陷中','身热头痛、咳嗽气喘'),
('acu-086','哑门','yǎ mén','GV15 Yamen','督脉','后发际正中直上0.5寸','暴喑不语、头痛项强'),
('acu-087','上星','shàng xīng','GV23 Shangxing','督脉','前发际正中直上1寸','头痛目眩、鼻渊鼻衄'),
('acu-088','水沟','shuǐ gōu','GV26 Shuigou','督脉','人中沟的上1/3与中1/3交点处','中风昏迷、癫狂痫证'),
-- 任脉补充
('acu-089','中极','zhōng jí','CV3 Zhongji','任脉','前正中线上，脐下4寸','遗尿遗精、月经不调'),
('acu-090','石门','shí mén','CV5 Shimen','任脉','前正中线上，脐下2寸','腹胀泄泻、水肿'),
('acu-091','下脘','xià wǎn','CV10 Xiawan','任脉','前正中线上，脐上2寸','胃痛、腹胀、呕吐'),
('acu-092','膻中','dàn zhōng','CV17 Danzhong','任脉','前正中线上，两乳头连线中点','胸闷气短、咳嗽气喘、乳汁少'),
('acu-093','天突','tiān tū','CV22 Tiantu','任脉','颈部前正中线上，胸骨上窝中央','咳嗽气喘、咽喉肿痛'),
('acu-094','廉泉','lián quán','CV23 Lianquan','任脉','舌骨体上缘中点处','舌下肿痛、舌强不语、吞咽困难'),
-- 经外奇穴
('acu-095','四神聪','sì shén cōng','EX-HN1 Sishencong','经外奇穴','百会穴前后左右各1寸','头痛眩晕、失眠健忘'),
('acu-096','安眠','ān mián','EX-HN22 Anmian','经外奇穴','翳风穴与风池穴连线中点','失眠、头痛眩晕'),
('acu-097','夹脊','jiā jǐ','EX-B2 Jiaji','经外奇穴','第1胸椎至第5腰椎棘突下两侧，后正中线旁开0.5寸','心肺疾病、腰背痛'),
('acu-098','腰眼','yāo yǎn','EX-B7 Yaoyan','经外奇穴','第4腰椎棘突下，旁开约3.5寸凹陷处','腰痛、月经不调'),
('acu-099','十宣','shí xuān','EX-UE11 Shixuan','经外奇穴','十指尖端，距指甲游离缘0.1寸','中风昏迷、中暑、高热惊厥'),
('acu-100','八邪','bā xié','EX-UE9 Baxie','经外奇穴','手背各指蹼缘后方赤白肉际处','手指麻木、烦热、目痛');

-- ----------------------------
-- 2. 扩充方剂 (补充42个,加上原有8个=50个)
-- ----------------------------
INSERT IGNORE INTO tcm_formula (id, name, category, description, source) VALUES
('formula-9',  '麻黄汤',     '解表剂', '发汗解表，宣肺平喘',           '《伤寒论》'),
('formula-10', '银翘散',     '解表剂', '辛凉透表，清热解毒',           '《温病条辨》'),
('formula-11', '白虎汤',     '清热剂', '清热生津',                     '《伤寒论》'),
('formula-12', '龙胆泻肝汤', '清热剂', '清泻肝胆实火，清利湿热',       '《医方集解》'),
('formula-13', '大承气汤',   '泻下剂', '峻下热结',                     '《伤寒论》'),
('formula-14', '小承气汤',   '泻下剂', '轻下热结',                     '《伤寒论》'),
('formula-15', '半夏泻心汤', '和解剂', '和胃降逆，开结除痞',           '《伤寒论》'),
('formula-16', '四逆散',     '和解剂', '透邪解郁，疏肝理脾',           '《伤寒论》'),
('formula-17', '理中汤',     '温里剂', '温中祛寒，补气健脾',           '《伤寒论》'),
('formula-18', '四逆汤',     '温里剂', '回阳救逆',                     '《伤寒论》'),
('formula-19', '小建中汤',   '温里剂', '温中补虚，和里缓急',           '《伤寒论》'),
('formula-20', '当归四逆汤', '温里剂', '温经散寒，养血通脉',           '《伤寒论》'),
('formula-21', '二陈汤',     '祛痰剂', '燥湿化痰，理气和中',           '《太平惠民和剂局方》'),
('formula-22', '温胆汤',     '祛痰剂', '理气化痰，和胃利胆',           '《三因极一病证方论》'),
('formula-23', '平胃散',     '祛湿剂', '燥湿运脾，行气和胃',           '《太平惠民和剂局方》'),
('formula-24', '五苓散',     '祛湿剂', '利水渗湿，温阳化气',           '《伤寒论》'),
('formula-25', '真武汤',     '祛湿剂', '温阳利水',                     '《伤寒论》'),
('formula-26', '独活寄生汤', '祛风湿剂','祛风湿，止痹痛，益肝肾',     '《备急千金要方》'),
('formula-27', '血府逐瘀汤', '理血剂', '活血化瘀，行气止痛',           '《医林改错》'),
('formula-28', '生化汤',     '理血剂', '养血祛瘀，温经止痛',           '《傅青主女科》'),
('formula-29', '归脾汤',     '补益剂', '益气补血，健脾养心',           '《济生方》'),
('formula-30', '天王补心丹', '安神剂', '滋阴养血，补心安神',           '《校注妇人良方》'),
('formula-31', '酸枣仁汤',   '安神剂', '养血安神，清热除烦',           '《金匮要略》'),
('formula-32', '苓桂术甘汤', '祛痰剂', '温阳化饮，健脾利湿',           '《金匮要略》'),
('formula-33', '生脉散',     '补益剂', '益气生津，敛阴止汗',           '《内外伤辨惑论》'),
('formula-34', '玉屏风散',   '固涩剂', '益气固表止汗',                 '《丹溪心法》'),
('formula-35', '四物汤',     '补益剂', '补血调血',                     '《太平惠民和剂局方》'),
('formula-36', '炙甘草汤',   '补益剂', '益气滋阴，通阳复脉',           '《伤寒论》'),
('formula-37', '参苓白术散', '补益剂', '益气健脾，渗湿止泻',           '《太平惠民和剂局方》'),
('formula-38', '左归丸',     '补益剂', '滋阴补肾，填精益髓',           '《景岳全书》'),
('formula-39', '右归丸',     '补益剂', '温补肾阳，填精止遗',           '《景岳全书》'),
('formula-40', '大补阴丸',   '补益剂', '滋阴降火',                     '《丹溪心法》'),
('formula-41', '保和丸',     '消食剂', '消食和胃',                     '《丹溪心法》'),
('formula-42', '越鞠丸',     '理气剂', '行气解郁',                     '《丹溪心法》'),
('formula-43', '天麻钩藤饮', '治风剂', '平肝熄风，清热活血，补益肝肾', '《杂病证治新义》'),
('formula-44', '镇肝熄风汤', '治风剂', '镇肝熄风，滋阴潜阳',           '《医学衷中参西录》'),
('formula-45', '止嗽散',     '止咳剂', '止咳化痰，疏表宣肺',           '《医学心悟》'),
('formula-46', '杏苏散',     '解表剂', '轻宣凉燥，理肺化痰',           '《温病条辨》'),
('formula-47', '败毒散',     '解表剂', '散寒祛湿，益气解表',           '《太平惠民和剂局方》'),
('formula-48', '藿香正气散', '祛湿剂', '解表化湿，理气和中',           '《太平惠民和剂局方》'),
('formula-49', '六一散',     '祛暑剂', '清暑利湿',                     '《伤寒直格》'),
('formula-50', '桑菊饮',     '解表剂', '疏风清热，宣肺止咳',           '《温病条辨》');

-- 方剂明细 (部分核心方剂)
INSERT IGNORE INTO tcm_formula_item (formula_id, herb_name, dosage, unit, sort_order) VALUES
-- 麻黄汤
('formula-9','麻黄',9,'g',1),('formula-9','桂枝',6,'g',2),('formula-9','杏仁',6,'g',3),('formula-9','甘草',3,'g',4),
-- 银翘散
('formula-10','金银花',15,'g',1),('formula-10','连翘',15,'g',2),('formula-10','薄荷',6,'g',3),('formula-10','桔梗',6,'g',4),('formula-10','甘草',5,'g',5),
-- 白虎汤
('formula-11','石膏',50,'g',1),('formula-11','知母',18,'g',2),('formula-11','甘草',6,'g',3),('formula-11','粳米',9,'g',4),
-- 半夏泻心汤
('formula-15','法半夏',12,'g',1),('formula-15','黄芩',9,'g',2),('formula-15','干姜',9,'g',3),('formula-15','党参',9,'g',4),('formula-15','甘草',9,'g',5),('formula-15','黄连',3,'g',6),('formula-15','大枣',12,'g',7),
-- 理中汤
('formula-17','党参',15,'g',1),('formula-17','白术',15,'g',2),('formula-17','干姜',10,'g',3),('formula-17','甘草',6,'g',4),
-- 四逆汤
('formula-18','附子',15,'g',1),('formula-18','干姜',10,'g',2),('formula-18','甘草',6,'g',3),
-- 二陈汤
('formula-21','法半夏',15,'g',1),('formula-21','陈皮',15,'g',2),('formula-21','茯苓',10,'g',3),('formula-21','甘草',5,'g',4),
-- 五苓散
('formula-24','猪苓',9,'g',1),('formula-24','泽泻',15,'g',2),('formula-24','白术',9,'g',3),('formula-24','茯苓',9,'g',4),('formula-24','桂枝',6,'g',5),
-- 归脾汤
('formula-29','党参',12,'g',1),('formula-29','黄芪',12,'g',2),('formula-29','白术',10,'g',3),('formula-29','茯苓',12,'g',4),('formula-29','当归',10,'g',5),('formula-29','酸枣仁',12,'g',6),('formula-29','远志',6,'g',7),('formula-29','甘草',5,'g',8),
-- 酸枣仁汤
('formula-31','酸枣仁',15,'g',1),('formula-31','茯苓',10,'g',2),('formula-31','知母',10,'g',3),('formula-31','川芎',6,'g',4),('formula-31','甘草',3,'g',5),
-- 四物汤
('formula-35','当归',10,'g',1),('formula-35','白芍',12,'g',2),('formula-35','熟地黄',12,'g',3),('formula-35','川芎',8,'g',4),
-- 玉屏风散
('formula-34','黄芪',20,'g',1),('formula-34','白术',12,'g',2),('formula-34','防风',10,'g',3),
-- 生脉散
('formula-33','人参',10,'g',1),('formula-33','麦冬',15,'g',2),('formula-33','五味子',6,'g',3),
-- 参苓白术散
('formula-37','党参',15,'g',1),('formula-37','茯苓',10,'g',2),('formula-37','白术',10,'g',3),('formula-37','山药',10,'g',4),('formula-37','薏苡仁',10,'g',5),('formula-37','甘草',5,'g',6),
-- 桑菊饮
('formula-50','桑叶',7.5,'g',1),('formula-50','菊花',3,'g',2),('formula-50','杏仁',6,'g',3),('formula-50','连翘',5,'g',4),('formula-50','薄荷',2.5,'g',5),('formula-50','桔梗',6,'g',6),('formula-50','甘草',2.5,'g',7);

-- ============================================================
-- v6: 库存表增加 herb_dict_id + payload JSON 列
-- 用于关联中药材字典，以及存储别名、性味归经、功效、禁忌、进价等附加属性
-- 在执行 tcm_upgrade_v5 之后执行此脚本
-- ============================================================

-- 1. 库存表增加 herb_dict_id（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_inventory_item' AND COLUMN_NAME = 'herb_dict_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE tcm_inventory_item ADD COLUMN herb_dict_id varchar(64) DEFAULT NULL COMMENT ''关联中药材字典ID'' AFTER deleted_at', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 库存表增加 payload（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_inventory_item' AND COLUMN_NAME = 'payload');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE tcm_inventory_item ADD COLUMN payload TEXT DEFAULT NULL COMMENT ''扩展JSON（别名、性味归经、功效禁忌、进价等）'' AFTER herb_dict_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 方剂明细表增加 herb_dict_id（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_formula_item' AND COLUMN_NAME = 'herb_dict_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE tcm_formula_item ADD COLUMN herb_dict_id varchar(64) DEFAULT NULL COMMENT ''关联中药材字典ID'' AFTER notes', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. herb 字典增加 toxicity（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_herb_dict' AND COLUMN_NAME = 'toxicity');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE tcm_herb_dict ADD COLUMN toxicity varchar(50) DEFAULT NULL COMMENT ''毒性'' AFTER taste', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. 补充 practitioner 角色的方剂管理权限（幂等）
INSERT IGNORE INTO sys_role_menu VALUES (100,2811),(100,2812),(100,2813);

-- ============================================================
-- v7: 插入默认报价单（含常用收费项目）
-- 在执行 tcm_upgrade_v6 之后执行此脚本
-- 幂等：使用 INSERT IGNORE，重复执行不会出错
-- ============================================================

INSERT IGNORE INTO tcm_price_list (id, name, effective_date, is_active, items_json) VALUES
('pricelist-default', 'Standard Pricing', CURDATE(), 1,
 '[{"name":"Acupuncture - New Patient","price":120,"taxable":true},{"name":"Acupuncture - Follow Up","price":80,"taxable":true},{"name":"Herbal Consultation","price":60,"taxable":true},{"name":"Cupping Therapy","price":50,"taxable":true},{"name":"Moxibustion","price":40,"taxable":true},{"name":"Tui Na Massage","price":70,"taxable":true},{"name":"Herbal Formula - Powder","price":0,"taxable":false},{"name":"Herbal Formula - Raw Herbs","price":0,"taxable":false},{"name":"Herbal Formula - Pills","price":0,"taxable":false},{"name":"Consultation Only","price":30,"taxable":true},{"name":"Follow-up Consultation","price":20,"taxable":true},{"name":"Ear Acupuncture","price":35,"taxable":true},{"name":"Electro-Acupuncture","price":90,"taxable":true},{"name":"Gua Sha","price":45,"taxable":true}]');

UPDATE tcm_price_list
SET name = 'Standard Pricing',
    effective_date = COALESCE(effective_date, CURDATE()),
    is_active = 1,
    items_json = '[{"name":"Acupuncture - New Patient","price":120,"taxable":true},{"name":"Acupuncture - Follow Up","price":80,"taxable":true},{"name":"Herbal Consultation","price":60,"taxable":true},{"name":"Cupping Therapy","price":50,"taxable":true},{"name":"Moxibustion","price":40,"taxable":true},{"name":"Tui Na Massage","price":70,"taxable":true},{"name":"Herbal Formula - Powder","price":0,"taxable":false},{"name":"Herbal Formula - Raw Herbs","price":0,"taxable":false},{"name":"Herbal Formula - Pills","price":0,"taxable":false},{"name":"Consultation Only","price":30,"taxable":true},{"name":"Follow-up Consultation","price":20,"taxable":true},{"name":"Ear Acupuncture","price":35,"taxable":true},{"name":"Electro-Acupuncture","price":90,"taxable":true},{"name":"Gua Sha","price":45,"taxable":true}]'
WHERE id = 'pricelist-default';
-- <<<<<<< END tcm_upgrade_all.sql
