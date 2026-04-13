## 刷题系统

这是一个前后端分离的考研政治刷题系统：

- `backend/`：Spring Boot 3 + MyBatis Plus
- `frontend/`：Vue 3 + Vite
- `deploy/`：Docker Compose、Nginx、MySQL、Redis 初始化脚本

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

## `kyzz` 数据集接入

当前仓库使用 `dataset_to_excel.py` 把外部题库转换成后台题库管理页可直接导入的 Excel 模板，已经适配 `mrwoov/kyzz` 的数据结构：

- 支持目录输入，会自动聚合 `dataset/kyzz/data` 下的全部 `.json/.jsonl`
- 支持提取 `detail.timu`
- 支持解析 `xuanxiang` / `xuan_text` / `right_text` / `jiexi` / `chuchu`
- 支持两种分类模式：
  - `top`：只映射到顶层专题
  - `chapter`：生成章节级 `categoryId`，并产出配套的 `category` seed SQL

## 生成命令

顶层专题版：

```bash
python dataset_to_excel.py ^
  --input dataset/kyzz/data ^
  --output dataset/exports/kyzz-2010-2024.xlsx
```

章节版：

```bash
python dataset_to_excel.py ^
  --input dataset/kyzz/data ^
  --category-mode chapter ^
  --category-seed-output deploy/seed_kyzz_categories.sql ^
  --output dataset/exports/kyzz-2010-2024-chapter.xlsx
```

## 当前产物

- `dataset/exports/kyzz-2010-2024.xlsx`
  - 顶层专题版
- `dataset/exports/kyzz-2010-2024-chapter.xlsx`
  - 章节版
- `deploy/seed_kyzz_categories.sql`
  - 45 个章节分类的 seed SQL

数据规模：

- 共 495 道题
- 覆盖 2010-2024 年考研真题

## 导入方式

### 方案 A：直接走后台导入

1. 启动后端和前端
2. 进入后台题库管理页
3. 上传 `dataset/exports/kyzz-2010-2024.xlsx`

### 方案 B：章节分类 + 后台导入

1. 先初始化基础库表和根专题
2. 执行 `deploy/seed_kyzz_categories.sql`
3. 启动后端和前端
4. 上传 `dataset/exports/kyzz-2010-2024-chapter.xlsx`

MySQL 示例：

```bash
mysql --default-character-set=utf8mb4 -uroot -p question_bank < deploy/seed_kyzz_categories.sql
```

后台导入接口：

`POST /api/admin/questions/import`

## 本地验证记录

当前工作区已经完成过一次真实导入验证：

- `question_bank.category`：`7 -> 52`
- 新增 45 个章节分类
- `question_bank.question`：`10 -> 505`
- 通过管理员接口成功导入 495 道题，`failCount = 0`

默认管理员：

- 邮箱：`admin@example.com`
- 密码：`admin123`

## 已知情况

- 源数据里一部分题目没有 `p_kaodian_text`，这部分会保留在顶层专题下，不会强行猜章节。
- 当前本地导入后，仍有一部分题目留在根专题：
  - `category_id = 1`：224 题
  - `category_id = 2`：3 题
  - `category_id = 4`：1 题

## 回归验证

转换脚本测试：

```bash
python -m unittest discover -s tests -p "test_*.py"
```
