# 青棚管家｜智能大棚 Android 大作业

一个面向小规模种植户的课程演示项目：关注节水、可追溯操作与人工决策。应用使用 Java、XML、Fragment、RecyclerView、SQLite 和网络请求。**棚内数值与设备控制均为本地演示，不代表真实传感器或执行器。**

## 运行

1. 使用 Android Studio 打开本目录，安装 Android SDK 37，运行 app 模块；或执行 Gradle 的 :app:assembleDebug 任务。生成的 APK 位于 app/build/outputs/apk/debug/app-debug.apk。
2. 演示账户为 farmer / 123456；输错会出现失败对话框。勾选“记住密码”后下次打开登录页时自动填写账号和密码，用户仍需点击“进入大棚”；退出登录不会清除已记住的内容。未勾选后成功登录会清除保存内容。
3. 首页显示室外温度、湿度和未来三日天气，每天单独一行，并显示省、市、区县名称。默认查看北京市 · 北京市 · 东城区。点击“定位天气”并允许位置权限可查询当前位置；“切换地区”支持搜索城市或区县。天气卡会对照当前作物适宜温度范围，室外天气仅供参考。
4. 可直接拍照或从相册选择照片，在设备端用颜色特征做作物初筛，支持草莓、西红柿、黄瓜、蝴蝶兰、桃子、葡萄。识别结果需要人工确认后才会应用作物档案；切换作物会自动载入该作物温度上限及模拟控制阈值。适宜温度参考范围分别为 15–25°C、20–28°C、20–30°C、18–28°C、18–28°C、20–30°C。数值会因品种和生长阶段变化；空气湿度、土壤湿度及光照阈值为演示预设，需要按传感器标定，不能直接用于生产。
5. 设备页的每台设备都可切换自动/手动模式。自动模式下，通风机按温度和空气湿度上限启停，灌溉水泵按土壤湿度下限启停，补光灯按光照下限启停；手动模式下可直接开关，自动规则不会覆盖手动状态。历史页只显示自动阈值触发的设备变化。超过 7 天的记录每周自动清理；左滑可确认删除单条。
6. 购物页提供农药和农资关键词输入，点击淘宝或京东按钮后通过浏览器或平台应用打开对应搜索结果。商品、购买和支付由外部电商平台处理，本应用不保存订单。
7. “AI 植物问诊”完全在手机本机运行，不需要填写网址或启动其他服务。输入症状或上传植物照片后，应用会结合本地病害特征、照片颜色线索和棚内环境，给出常见病症候选、处理步骤和药剂有效成分方向。回答会提示核对农药登记、产品标签和采收安全间隔，不能替代植保人员诊断。
8. 首页和购物页使用本地温室照片轮播，可手动左右滑动，并在页面可见时每 4 秒自动切换。界面配色改为泥土色、橄榄绿和暖米色。

本项目使用 Android 11 语言级别语法，最低支持 Android 7.0（API 24）。课程账号采用本地硬编码，仅用于功能演示；正式部署须改为受保护的服务端身份认证。

## 轮播照片来源

以下照片均随应用打包，轮播无需联网：

- `carousel_tomatoes.jpg`：Alexander Roumega，CC0，Wikimedia Commons：<https://commons.wikimedia.org/wiki/File:Greenhouse_with_tomatoes_in_Chebachiy.jpg>
- `carousel_strawberries.jpg`：Tomwsulcer，CC0，Wikimedia Commons：<https://commons.wikimedia.org/wiki/File:Great_Swamp_Greenhouse_photos_Strawberry_Junebearing.JPG>
- `carousel_irrigation.jpg`：USDAgov，Public domain，Wikimedia Commons：<https://commons.wikimedia.org/wiki/File:Irrigation_and_Water_(20150827-NRCS-LSC-0340).jpg>

## 离线植物问诊

问诊页使用 `LocalPlantAdvisor` 的本地规则，不发送文字或照片，也不依赖网络服务。规则会识别白粉病、灰霉病、霜霉或叶斑、蚜虫、叶螨、根腐等常见候选，并给出清除病株、通风控水、诱虫板和药剂有效成分方向。照片分析是颜色特征初筛，症状不典型时会降低把握度并要求补拍叶片正反面、茎基部和根区。

药剂只展示有效成分方向，不给出未经核实的剂量。实际使用前必须核对当地登记的作物和防治对象、产品标签、个人防护及采收安全间隔。

## 评分点对照

| 评分点 | 实现位置 |
| --- | --- |
| 基本组件、布局、资源、美观 | TextView、EditText、Button、Spinner、SwitchMaterial、ScrollView、CardView；res/layout 与 res/values/strings.xml |
| 界面、Intent、登录 | LoginActivity → MainActivity；DashboardFragment → AdviceActivity；登录错误对话框 |
| 高级组件、Fragment、列表 | 作物 Spinner 与图片识别；4 个底部导航 Fragment；记录 RecyclerView |
| 详情、网络数据 | AdviceActivity 的离线问诊；DashboardFragment 的 Open-Meteo HTTPS 请求 |
| 扩展 | PopupMenu、MaterialAlertDialog、SQLite、SharedPreferences |
| AI 技术 | LocalPlantAdvisor 本地规则问诊，支持症状文字、照片颜色线索和棚内环境数据 |

## 设计说明与工程责任

设计从小规模种植户的实际问题出发：先看环境，再确认设备操作，最后留存审计记录。草绿色系与卡片布局突出作物及节水主题，降低操作复杂度。天气只作为外界参考，不能代替棚内传感器；设备开关增加确认并标记“模拟”，避免误以为已经驱动硬件。真实工程应考虑断网、误报、执行器故障、人工紧急停止、隐私与生产数据管理、用水与能耗成本，以及农业设施安全和相关法规。AI 建议只做辅助，不直接参与控制闭环。

## 答辩演示顺序

1. 错误密码 → 提示；正确密码 → Intent 跳转。
2. 指出基本组件、strings.xml、Spinner 和首页网络天气；断网时展示重试状态。
3. 作物切换 → 本地建议变化 → 详情 Activity。
4. 设备切换 → 确认对话框 → SQLite 列表新增记录。
5. 更多菜单 → 关于与退出；解释模拟数据和离线问诊的判断边界。

## AI 使用过程记录

见 docs/AI_USAGE.md。
