# 奇漫屋公开书目扫描

在项目根目录运行 `python tools/qiman/scan_catalog.py`。脚本从官网分类页发现第一层类型、状态、标签入口及更新时间/人气排序，读取这些入口公开列出的数字分页。最多六个并发，只下载列表 HTML，不请求正文、封面文件或账号接口。

扫描缓存位于 `build/qiman-scan`，重跑时复用成功页面。完成后生成 `manifest.tsv` 和 `report.json`；报告保留失败地址，不能把有失败页的扫描称为完整扫描。`python tools/qiman/scan_progress.py` 可生成中间进度清单。

用 `tests/BuildScannedQimanCatalog.java` 和生产 `SiteIndex` 解析器将清单构建为 `app/src/main/assets/qiman-catalog.tsv`。`tests/VerifyScannedQimanTraversal.java` 在已下载页面上回放手机扫描器，验证分类发现、分页与边界规则。`tests/QimanCatalogDataTest.java` 验证最终数据的去重、地址、封面和关键词匹配。

手机扫描器 `QimanCatalogScanner` 使用相同的分类发现方式，按下一页顺序遍历。它将书目、已扫描和待扫描地址放进一个检查点；Activity 用 Android AtomicFile 保存，避免覆盖写入中断损坏已有文件。每次扫描会暂时跳过失败页，下次继续重试。完成一轮后，再次扫描会从首页和分类页重新发现入口。

覆盖范围是官网公开分类能发现的作品。某些分类分页被网站限制为前 50 页；未公开、未归类、已失效的作品，以及只出现在更复杂筛选组合中的作品，不保证被收录。数据不代表网站后台完整数据库。
