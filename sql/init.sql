create table if not exists address_book
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    user_id       bigint                               not null comment '用户ID',
    consignee     varchar(50)                          not null comment '收货人',
    sex           varchar(2)                           null comment '性别',
    phone         varchar(11)                          not null comment '手机号',
    province_code varchar(12)                          null comment '省级区划码',
    province_name varchar(32)                          null comment '省级名称',
    city_code     varchar(12)                          null comment '市级区划码',
    city_name     varchar(32)                          null comment '市级名称',
    district_code varchar(12)                          null comment '区级区划码',
    district_name varchar(32)                          null comment '区级名称',
    detail        varchar(200)                         null comment '详细地址',
    label         varchar(100)                         null comment '地址标签',
    is_default    tinyint(1) default 0                 not null comment '是否默认地址',
    create_time   datetime   default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '地址簿' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on address_book (user_id)
    comment '用户ID索引';

create table if not exists category
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    type        int           null comment '分类类型',
    name        varchar(32)   not null comment '分类名称',
    sort        int default 0 not null comment '排序',
    status      int default 1 not null comment '状态',
    create_time datetime      not null comment '创建时间',
    update_time datetime      not null comment '更新时间',
    create_user bigint        null comment '创建人',
    update_user bigint        null comment '更新人'
)
    comment '菜品及套餐分类' collate = utf8mb4_unicode_ci;

create index idx_type_status
    on category (type, status)
    comment '分类类型+状态联合索引';

create table if not exists coupon
(
    id               bigint auto_increment comment '主键'
        primary key,
    coupon_name      varchar(64)                              not null comment '优惠券名称',
    coupon_type      tinyint        default 1                 not null comment '类型 1满减 2直减',
    threshold_amount decimal(10, 2) default 0.00              not null comment '满减门槛（0=无门槛）',
    discount_amount  decimal(10, 2)                           not null comment '优惠金额',
    total_stock      int                                      not null comment '总发放数量',
    surplus_stock    int            default 0                 not null comment '剩余库存',
    start_time       datetime                                 not null comment '领取开始时间',
    end_time         datetime                                 not null comment '领取结束时间',
    valid_days       int            default 7                 not null comment '领取后有效天数',
    status           tinyint        default 0                 not null comment '状态 0未开始 1进行中 2已结束',
    create_user      bigint                                   not null comment '创建人ID',
    update_user      bigint                                   null comment '修改人ID',
    create_time      datetime       default CURRENT_TIMESTAMP null comment '创建时间',
    update_time      datetime       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    is_seckill       tinyint        default 0                 not null comment '是否秒杀优惠券 0=普通发放券 1=秒杀抢购券'
)
    comment '优惠券表';

create index idx_is_seckill
    on coupon (is_seckill);

create index idx_status
    on coupon (status);

create index idx_surplus_stock
    on coupon (surplus_stock);

create table if not exists dish
(
    id             bigint auto_increment comment '主键id'
        primary key,
    name           varchar(32)                        not null comment '菜品名称',
    category_id    bigint                             not null comment '分类id',
    price          decimal(10, 2)                     not null comment '菜品原价',
    spike_price    decimal(10, 2)                     null comment '秒杀价格',
    image          varchar(255)                       null comment '菜品图片',
    description    varchar(255)                       null comment '菜品描述',
    status         tinyint  default 1                 not null comment '状态 0停售 1起售',
    create_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    create_user    bigint                             null comment '创建人id',
    update_user    bigint                             null comment '更新人id',
    stock          int      default 0                 not null comment '库存',
    activity_stock int                                null comment '活动库存',
    is_spike       tinyint  default 0                 not null comment '是否秒杀 0否 1是'
)
    comment '菜品表';

create index idx_category_id
    on dish (category_id);

create index idx_spike_status
    on dish (is_spike, status);

create index idx_status
    on dish (status);

create index idx_update_time
    on dish (update_time);

create table if not exists dish_flavor
(
    id      bigint auto_increment comment '主键ID'
        primary key,
    dish_id bigint       not null comment '菜品ID',
    name    varchar(32)  null comment '口味名称',
    value   varchar(255) null comment '口味值'
)
    comment '菜品口味关系表' collate = utf8mb4_unicode_ci;

create index idx_dish_id
    on dish_flavor (dish_id)
    comment '菜品ID索引';

create table if not exists employee
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    name        varchar(32)                    null comment '员工姓名',
    username    varchar(32)                    not null comment '用户名',
    password    varchar(64)                    not null comment '密码',
    phone       varchar(11)                    not null comment '手机号',
    sex         varchar(2)                     null comment '性别',
    id_number   varchar(18)                    null comment '身份证号',
    status      int         default 1          not null comment '状态',
    merchant_id bigint      default 1          not null comment '商家ID(单店模式固定为1)',
    role        varchar(32) default 'EMPLOYEE' not null comment '角色(BOSS:老板, EMPLOYEE:普通员工)',
    create_time datetime                       not null comment '创建时间',
    update_time datetime                       not null comment '更新时间',
    create_user bigint                         null comment '创建人',
    update_user bigint                         null comment '更新人',
    constraint uk_username
        unique (username) comment '用户名唯一索引'
)
    comment '员工信息表' collate = utf8mb4_unicode_ci;

create index idx_status
    on employee (status)
    comment '状态索引';

create table if not exists order_detail
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    name        varchar(32)    null comment '商品名称',
    image       varchar(255)   null comment '商品图片',
    order_id    bigint         not null comment '订单ID',
    dish_id     bigint         null comment '菜品ID',
    dish_flavor varchar(50)    null comment '菜品口味',
    number      int default 0  not null comment '商品数量',
    amount      decimal(10, 2) not null comment '商品金额'
)
    comment '订单明细表' collate = utf8mb4_unicode_ci;

create index idx_order_id
    on order_detail (order_id)
    comment '订单ID索引';

create table if not exists orders
(
    id                      bigint auto_increment comment '主键ID'
        primary key,
    number                  varchar(50)       not null comment '订单号',
    status                  int               not null comment '订单状态',
    user_id                 bigint            not null comment '用户ID',
    address_book_id         bigint            not null comment '地址ID',
    order_time              datetime          not null comment '下单时间',
    checkout_time           datetime          null comment '结账时间',
    pay_method              int               null comment '支付方式',
    pay_status              tinyint default 0 not null comment '支付状态',
    amount                  decimal(10, 2)    not null comment '订单金额',
    remark                  varchar(100)      null comment '备注',
    phone                   varchar(11)       null comment '收货人手机号',
    address                 varchar(255)      null comment '收货地址',
    user_name               varchar(32)       null comment '用户名',
    consignee               varchar(32)       null comment '收货人',
    cancel_reason           varchar(255)      null comment '取消原因',
    rejection_reason        varchar(255)      null comment '拒绝原因',
    cancel_time             datetime          null comment '取消时间',
    estimated_delivery_time datetime          null comment '预计送达时间',
    delivery_status         tinyint(1)        null comment '配送状态',
    delivery_time           datetime          null comment '送达时间',
    pack_amount             int               null comment '包装费',
    tableware_number        int               null comment '餐具数量',
    tableware_status        tinyint(1)        null comment '餐具配送状态',
    constraint uk_number
        unique (number) comment '订单号唯一索引'
)
    comment '订单主表' collate = utf8mb4_unicode_ci;

create index idx_status_pay_time
    on orders (status, pay_status, order_time)
    comment '状态+支付状态+下单时间联合索引';

create index idx_user_id
    on orders (user_id)
    comment '用户ID索引';

create table if not exists shopping_cart
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    name        varchar(32)    null comment '商品名称',
    image       varchar(255)   null comment '商品图片',
    user_id     bigint         not null comment '用户ID',
    dish_id     bigint         null comment '菜品ID',
    dish_flavor varchar(50)    null comment '菜品口味',
    number      int default 1  not null comment '商品数量',
    amount      decimal(10, 2) not null comment '商品金额',
    create_time datetime       not null comment '创建时间',
    constraint uk_user_dish_flavor
        unique (user_id, dish_id, dish_flavor) comment '用户+菜品+口味唯一索引'
)
    comment '购物车表' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on shopping_cart (user_id)
    comment '用户ID索引';

create table if not exists user
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    openid      varchar(45) not null comment '微信openid',
    create_time datetime    not null comment '创建时间',
    constraint uk_openid
        unique (openid) comment 'openid唯一索引'
)
    comment '用户信息表' collate = utf8mb4_unicode_ci;

create table if not exists user_coupon
(
    id               bigint auto_increment
        primary key,
    user_id          bigint                                   not null comment '用户ID',
    coupon_id        bigint                                   not null comment '优惠券ID',
    coupon_name      varchar(64)                              not null comment '优惠券名称',
    coupon_type      tinyint        default 1                 not null comment '类型 1满减 2直减',
    threshold_amount decimal(10, 2) default 0.00              not null comment '满减门槛',
    discount_amount  decimal(10, 2)                           not null comment '优惠金额',
    get_time         datetime       default CURRENT_TIMESTAMP null comment '领取时间',
    expire_time      datetime                                 not null comment '优惠券过期时间',
    is_seckill       tinyint        default 0                 not null comment '是否秒杀券 0=普通 1=秒杀',
    use_time         datetime                                 null comment '使用时间',
    order_id         bigint                                   null comment '关联订单ID',
    status           tinyint        default 0                 not null comment '优惠券状态：0-未使用 1-已使用 2-已过期',
    create_time      datetime       default CURRENT_TIMESTAMP null,
    constraint uk_user_coupon
        unique (user_id, coupon_id)
)
    comment '用户优惠券表';

create index idx_status
    on user_coupon (status);

create index idx_user_status_expire
    on user_coupon (user_id, status, expire_time);

INSERT INTO smart_ordering.coupon (id, coupon_name, coupon_type, threshold_amount, discount_amount, total_stock, surplus_stock, start_time, end_time, valid_days, status, create_user, update_user, create_time, update_time, is_seckill) VALUES (1, '外卖无门槛3元券', 2, 0.00, 3.00, 1000, 9845, '2026-05-18 20:58:32', '2099-12-31 23:59:59', 9999, 1, 1, 1, '2026-05-18 20:58:32', '2026-07-06 00:32:21', 1);
INSERT INTO smart_ordering.coupon (id, coupon_name, coupon_type, threshold_amount, discount_amount, total_stock, surplus_stock, start_time, end_time, valid_days, status, create_user, update_user, create_time, update_time, is_seckill) VALUES (2, '外卖满30减5元券', 1, 30.00, 5.00, 1000, 1000, '2026-05-18 20:58:32', '2099-12-31 23:59:59', 9999, 1, 1, 1, '2026-05-18 20:58:32', '2026-05-18 20:58:32', 0);

INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (46, '王老吉', 11, 6.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/41bfcacf-7ad4-4927-8b26-df366553a94c.png', '', 1, '2022-06-09 22:40:47', '2026-05-21 11:29:26', 1, 0, 1, 10001, 1);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (47, '北冰洋', 11, 4.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4451d4be-89a2-4939-9c69-3a87151cb979.png', '还是小时候的味道', 1, '2022-06-10 09:18:49', '2026-05-18 00:11:42', 1, 0, 80, 1000, 1);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (48, '雪花啤酒', 11, 4.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/bf8cbfc1-04d2-40e8-9826-061ee41ab87c.png', '', 1, '2022-06-10 09:22:54', '2022-06-10 09:22:54', 1, 1, 80, 1000, 1);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (49, '米饭', 12, 2.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/76752350-2121-44d2-b477-10791c23a8ec.png', '精选五常大米', 1, '2022-06-10 09:30:17', '2022-06-10 09:30:17', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (50, '馒头', 12, 1.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/475cc599-8661-4899-8f9e-121dd8ef7d02.png', '优质面粉', 1, '2022-06-10 09:34:28', '2022-06-10 09:34:28', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (51, '老坛酸菜鱼', 20, 56.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4a9cefba-6a74-467e-9fde-6e687ea725d7.png', '原料：汤，草鱼，酸菜', 1, '2022-06-10 09:40:51', '2022-06-10 09:40:51', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (52, '经典酸菜鮰鱼', 20, 66.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/5260ff39-986c-4a97-8850-2ec8c7583efc.png', '原料：酸菜，江团，鮰鱼', 1, '2022-06-10 09:46:02', '2022-06-10 09:46:02', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (53, '蜀味水煮草鱼', 20, 38.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a6953d5a-4c18-4b30-9319-4926ee77261f.png', '原料：草鱼，汤', 1, '2022-06-10 09:48:37', '2022-06-10 09:48:37', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (54, '清炒小油菜', 19, 18.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/3613d38e-5614-41c2-90ed-ff175bf50716.png', '原料：小油菜', 1, '2022-06-10 09:51:46', '2022-06-10 09:51:46', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (55, '蒜蓉娃娃菜', 19, 18.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4879ed66-3860-4b28-ba14-306ac025fdec.png', '原料：蒜，娃娃菜', 1, '2022-06-10 09:53:37', '2022-06-10 09:53:37', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (56, '清炒西兰花', 19, 18.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/e9ec4ba4-4b22-4fc8-9be0-4946e6aeb937.png', '原料：西兰花', 1, '2022-06-10 09:55:44', '2022-06-10 09:55:44', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (57, '炝炒圆白菜', 19, 18.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/22f59feb-0d44-430e-a6cd-6a49f27453ca.png', '原料：圆白菜', 1, '2022-06-10 09:58:35', '2022-06-10 09:58:35', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (58, '清蒸鲈鱼', 18, 98.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/c18b5c67-3b71-466c-a75a-e63c6449f21c.png', '原料：鲈鱼', 1, '2022-06-10 10:12:28', '2022-06-10 10:12:28', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (59, '东坡肘子', 18, 138.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a80a4b8c-c93e-4f43-ac8a-856b0d5cc451.png', '原料：猪肘棒', 1, '2022-06-10 10:24:03', '2026-06-21 22:30:21', 1, 0, 79, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (60, '梅菜扣肉', 18, 58.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/6080b118-e30a-4577-aab4-45042e3f88be.png', '原料：猪肉，梅菜', 1, '2022-06-10 10:26:03', '2022-06-10 10:26:03', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (61, '剁椒鱼头', 18, 66.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/13da832f-ef2c-484d-8370-5934a1045a06.png', '原料：鲢鱼，剁椒', 1, '2022-06-10 10:28:54', '2022-06-10 10:28:54', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (62, '金汤酸菜牛蛙', 17, 88.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', '原料：鲜活牛蛙，酸菜', 1, '2022-06-10 10:33:05', '2026-05-17 17:36:24', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (63, '香锅牛蛙', 17, 88.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', '配料：鲜活牛蛙，莲藕，青笋', 1, '2022-06-10 10:35:40', '2022-06-10 10:35:40', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (64, '馋嘴牛蛙', 17, 88.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', '配料：鲜活牛蛙，丝瓜，黄豆芽', 1, '2022-06-10 10:37:52', '2026-05-17 17:36:24', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (65, '草鱼2斤', 16, 68.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/b544d3ba-a1ae-4d20-a860-81cb5dec9e03.png', '原料：草鱼，黄豆芽，莲藕', 1, '2022-06-10 10:41:08', '2022-06-10 10:41:08', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (66, '江团鱼2斤', 16, 119.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a101a1e9-8f8b-47b2-afa4-1abd47ea0a87.png', '配料：江团鱼，黄豆芽，莲藕', 1, '2022-06-10 10:42:42', '2022-06-10 10:42:42', 1, 1, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (67, '鮰鱼2斤', 16, 72.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/8cfcc576-4b66-4a09-ac68-ad5b273c2590.png', '原料：鮰鱼，黄豆芽，莲藕', 1, '2022-06-10 10:43:56', '2026-05-17 17:49:44', 1, 0, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (68, '鸡蛋汤', 21, 4.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/c09a0ee8-9d19-428d-81b9-746221824113.png', '配料：鸡蛋，紫菜', 1, '2022-06-10 10:54:25', '2026-05-17 17:49:44', 1, 0, 80, 0, 0);
INSERT INTO smart_ordering.dish (id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike) VALUES (69, '平菇豆腐汤', 21, 6.00, null, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/16d0a3d6-2253-4cfc-9b49-bf7bd9eb2ad2.png', '配料：豆腐，平菇', 1, '2022-06-10 10:55:02', '2022-06-10 10:55:02', 1, 1, 80, 0, 0);

INSERT INTO smart_ordering.employee (id, name, username, password, phone, sex, id_number, status, merchant_id, role, create_time, update_time, create_user, update_user) VALUES (1, '管理员', 'admin', '$2a$10$fdCnesvSfj7g3.I7b3Ua4.5N04Sv05ks0fyoy1Z8xP7mMK1UM9VJ6', '13812312312', '1', '110101199001010047', 1, 1, 'EMPLOYEE', '2022-02-15 15:51:20', '2022-02-17 09:16:20', 10, 1);
INSERT INTO smart_ordering.employee (id, name, username, password, phone, sex, id_number, status, merchant_id, role, create_time, update_time, create_user, update_user) VALUES (2, '张三', 'zs', '$2a$10$fdCnesvSfj7g3.I7b3Ua4.5N04Sv05ks0fyoy1Z8xP7mMK1UM9VJ6', '13812312314', '1', '110101199001010048', 0, 1, 'EMPLOYEE', '2025-05-19 20:30:26', '2025-05-23 10:54:58', 10, 1);
INSERT INTO smart_ordering.employee (id, name, username, password, phone, sex, id_number, status, merchant_id, role, create_time, update_time, create_user, update_user) VALUES (5, '王五', 'wangwu', '$2a$10$fdCnesvSfj7g3.I7b3Ua4.5N04Sv05ks0fyoy1Z8xP7mMK1UM9VJ6', '18414882345', '1', '111222333444555666', 1, 1, 'EMPLOYEE', '2025-05-20 08:27:40', '2025-05-20 08:27:40', 1, 1);
