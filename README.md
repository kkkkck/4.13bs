## 刷题系统

这是一个前后端分离的考研政治刷题系统：

- `backend/`：Spring Boot 3 + MyBatis Plus
- `frontend/`：Vue 3 + Vite
- `deploy/`：Docker Compose、Nginx、MySQL、Redis 和初始化脚本

## 本地启动

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

## `kyzz` 数据集一键导入

当前仓库已经适配 `mrwoov/kyzz` 数据集，并提供一条可复现的导入链路：

1. 生成后台可导入的 Excel
2. 初始化基础库表和根专题
3. 导入章节分类 seed SQL
4. 登录后台管理员并调用 `/api/admin/questions/import`

### 先决条件

- MySQL 已启动，并且本机可用 `mysql` 命令行
- 后端已启动，默认地址为 `http://127.0.0.1:8080`
- 默认管理员账号仍为：
  - 邮箱：`admin@example.com`
  - 密码：`admin123`

### 直接执行

```bash
python deploy/import_kyzz.py --mysql-password <你的 MySQL 密码>
```

默认行为：

- 输入目录：`dataset/kyzz/data`
- 输出文件：`dataset/exports/kyzz-2010-2024-chapter.xlsx`
- 分类模式：`chapter`
- 章节 seed：`deploy/seed_kyzz_categories.sql`
- 数据库：`question_bank`

### 常见变体

只重新生成 Excel 和章节 seed：

```bash
python deploy/import_kyzz.py --skip-db-init --skip-category-seed --skip-api-import
```

只初始化数据库并导入章节分类，不调用后台导题：

```bash
python deploy/import_kyzz.py --skip-api-import --mysql-password <你的 MySQL 密码>
```

改为顶层专题模式导入：

```bash
python deploy/import_kyzz.py --category-mode top --skip-category-seed --mysql-password <你的 MySQL 密码>
```

如果你的环境不是默认地址，可以覆盖这些参数：

```bash
python deploy/import_kyzz.py \
  --backend-base-url http://127.0.0.1:8080 \
  --admin-account admin@example.com \
  --admin-password admin123 \
  --mysql-host 127.0.0.1 \
  --mysql-port 3306 \
  --mysql-user root \
  --mysql-password <你的 MySQL 密码>
```

## 手工生成命令

顶层专题版：

```bash
python dataset_to_excel.py --input dataset/kyzz/data --output dataset/exports/kyzz-2010-2024.xlsx
```

章节版：

```bash
python dataset_to_excel.py --input dataset/kyzz/data --category-mode chapter --category-seed-output deploy/seed_kyzz_categories.sql --output dataset/exports/kyzz-2010-2024-chapter.xlsx
```

章节 seed 手工导入：

```bash
mysql --default-character-set=utf8mb4 -uroot -p question_bank < deploy/seed_kyzz_categories.sql
```

## 当前产物

- `dataset/exports/kyzz-2010-2024.xlsx`
- `dataset/exports/kyzz-2010-2024-chapter.xlsx`
- `deploy/seed_kyzz_categories.sql`

数据规模：

- 共 495 道题
- 覆盖 2010-2024 年考研真题
- 章节分类 seed 共 45 条

## 本地验证记录

当前工作区已经完成过一次真实导入验证：

- `question_bank.category`：`7 -> 52`
- 其中 `45` 个是 `kyzz` 生成的章节分类，另有 `2` 个初始化脚本自带章节
- `question_bank.question`：`8 -> 503`
- 通过管理员接口成功导入 `495` 道题，`failCount = 0`
- 以上结果已在临时验证库 `question_bank_verify_20260414` 上复跑确认

## 已知情况

- 源数据里一部分题目没有 `p_kaodian_text`，这部分会保留在顶层专题下，不会强行猜章节。
- 当前本地导入后，仍有一部分题目留在根专题：
  - `category_id = 1`：223 题
  - `category_id = 2`：2 题
  - `category_id = 4`：1 题

## 回归验证

```bash
python -m unittest discover -s tests -p "test_*.py"
```
