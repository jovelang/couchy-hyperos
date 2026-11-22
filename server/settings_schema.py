# SPDX-License-Identifier: GPL-3.0-only
"""User-facing desktop controls; nonlisted settings remain available on the TV."""

SCHEMA = [
    {
        "title": "时间与天气",
        "items": [
            ("h24", "24 小时制", "bool"),
            (
                "dateFormat",
                "日期格式",
                [
                    "不显示",
                    "星期与日",
                    "简短日期",
                    "完整日期",
                    "日/月",
                    "月/日",
                    "年月日",
                    "11月14日 周六",
                ],
            ),
            ("showLunar", "显示农历", "bool"),
            ("showWeather", "显示天气", "bool"),
            ("showGreeting", "显示问候语", "bool"),
            ("showNickname", "加上昵称", "bool"),
            ("nickname", "昵称", "text"),
            ("showStatusBar", "显示时间与顶栏", "bool"),
        ],
    },
    {
        "title": "背景视频",
        "items": [
            ("randomVideoRefresh", "自动换视频", "bool"),
            (
                "builtinSource",
                "在线视频库",
                ["全部", "Apple", "Amazon", "社区一", "社区二"],
            ),
            ("videoSpeed", "播放速度", ["0.25 倍", "0.5 倍", "0.75 倍", "1 倍"]),
            (
                "scrimMode",
                "背景变暗位置",
                ["顶部与底部", "顶部", "底部", "整个画面", "不变暗"],
            ),
        ],
    },
    {
        "title": "屏保与休眠",
        "items": [
            ("saverEnabled", "使用桌面屏保", "bool"),
            ("saverMode", "屏保画面", ["渐变", "图片", "视频"]),
            ("saverAfterMinutes", "无操作多久后进入", "duration"),
            ("saverScreenOffMinutes", "屏保多久后熄屏", "duration"),
            ("saverStandbyMinutes", "熄屏多久后休眠", "duration"),
            ("saverFlow", "流动渐变", "bool"),
            ("saverPhotoRandom", "随机换图片", "bool"),
            ("saverVideoRandom", "随机换屏保视频", "bool"),
            (
                "saverVideoSource",
                "屏保视频库",
                ["全部", "Apple", "Amazon", "社区一", "社区二"],
            ),
        ],
    },
    {
        "title": "外观与应用",
        "items": [
            ("layout", "应用布局", ["分类横排", "应用网格", "单行应用"]),
            ("uiScale", "界面大小", ["自动", "75%", "90%", "100%", "115%", "130%"]),
            ("iconScale", "应用卡片大小", ["小", "较小", "标准", "较大", "大"]),
            ("spacing", "卡片间距", ["紧凑", "较紧凑", "标准", "宽松", "更宽松"]),
            ("cornerRadius", "卡片圆角", ["最小", "小", "标准", "大", "最大"]),
            ("uniformSystemIcons", "统一功能图标", "bool"),
            ("showCategoryNames", "显示分组名称", "bool"),
        ],
    },
    {
        "title": "信号源与遥控器",
        "items": [
            ("inputPreview", "信号源实时预览", "bool"),
            ("sourceTapAction", "信号源键 · 点按", "action"),
            ("sourceHoldAction", "信号源键 · 长按", "action"),
            ("menuTapAction", "菜单键 · 点按", "action"),
            ("menuHoldAction", "菜单键 · 长按", "action"),
        ],
    },
]
DURATIONS = [0, 1, 2, 5, 10, 15, 30, 60, 120]
ACTIONS = [
    "无操作",
    "显示信号源列表",
    "电视设置",
    "桌面设置",
    "切换下一个信号源（全屏）",
    "HDMI1",
    "HDMI2",
    "HDMI3",
    "HDMI4",
    "TV",
    "AV",
    "DTMB",
]


def schema():
    groups = []
    for group in SCHEMA:
        items = []
        for key, title, kind in group["items"]:
            item = {
                "key": key,
                "title": title,
                "type": "select"
                if isinstance(kind, list) or kind in ["duration", "action"]
                else kind,
            }
            if isinstance(kind, list):
                item["options"] = [{"value": i, "label": x} for i, x in enumerate(kind)]
            elif kind == "duration":
                item["options"] = [
                    {
                        "value": x,
                        "label": (
                            "不自动进入"
                            if key == "saverAfterMinutes"
                            else "保持亮屏"
                            if key == "saverScreenOffMinutes"
                            else "不自动休眠"
                        )
                        if x == 0
                        else f"{x} 分钟",
                    }
                    for x in DURATIONS
                ]
            elif kind == "action":
                item["options"] = [{"value": i, "label": x} for i, x in enumerate(ACTIONS)]
            items.append(item)
        groups.append({"title": group["title"], "items": items})
    return groups


def validate(patch):
    from core import ControlError

    definitions = {item["key"]: item for g in schema() for item in g["items"]}
    if not isinstance(patch, dict) or len(patch) > 50:
        raise ControlError("设置内容无效", 400)
    for k, v in patch.items():
        if k == "inputAliases":
            if not isinstance(v, dict) or any(
                n not in ACTIONS[5:] or not isinstance(s, str) or len(s) > 24 for n, s in v.items()
            ):
                raise ControlError("信号源名称无效", 400)
            continue
        item = definitions.get(k)
        if not item:
            raise ControlError("不支持修改这项设置：" + str(k), 400)
        if item["type"] == "bool" and type(v) is not bool:
            raise ControlError("开关值无效", 400)
        if item["type"] == "text" and (not isinstance(v, str) or len(v) > 48):
            raise ControlError("昵称最多 48 个字符", 400)
        if item["type"] == "select" and (
            type(v) is not int or v not in [x["value"] for x in item["options"]]
        ):
            raise ControlError("选项值无效", 400)
    result = dict(patch)
    if "saverEnabled" in result:
        result["saverOwner"] = 1 if result["saverEnabled"] else 0
    return result
