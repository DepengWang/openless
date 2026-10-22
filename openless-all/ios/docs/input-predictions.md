# iOS 离线候选与续词

2026-10-20 更新：中文仍使用 librime 1.13.1，输入词库替换为
[雾凇拼音](https://github.com/iDvel/rime-ice/tree/da1fbe602e38f26db846fa10120ee64c2b0324c0)
的完整常用字表与 20 万条高频基础词，共 208,757 条字音／词音。旧版为
rime-pinyin-simp 的 65,140 行基础词库。保留 Rime 组句、简拼、补全、
用户词频学习与已确认个人词典桥接。

英文原本只调用
[UITextChecker.completions](https://developer.apple.com/documentation/uikit/uitextchecker)；
这提供单词补全，不等于系统键盘的下一词模型。旧实现遇到空格就清空候选，
中文选词后也不做续词。现在采用
[SymSpell 的开放频率数据](https://github.com/wolfgarbe/SymSpell/tree/c239062ae02961df18ab7da1671d01b4388204e0)
提供 82,833 个英文词、115,314 个上下文词对，再由 UITextChecker 提供拼写纠错。
输入 `Thank ` 后提供 `you`，部分单词按前词和词频排序；候选保留原输入，
只有点击候选才替换，不在按空格时静默纠错。

中文从同一雾凇词库建立 178,855 条词语后缀关联。上屏 `你好` 后可选 `吗`、
`啊`，点击只追加未输入的部分。它是词语续词，不是神经网络自由生成，
也不是对完整句子语义的推理。优先匹配最近 4 个汉字，随后回退到更短上下文。

候选条在空格／上屏后继续显示，左侧 44 pt 返回按钮可恢复语言、风格与语音工具栏。
URL、邮箱、密码、验证码、用户名和选中文本不显示统计候选；
英文单词中间及中文后接已有汉字时不补词。选择前会核对文档 ID 和光标两侧文本，
过期候选不会删除或写入其他位置。

## 数据与资源

- 数据源固定提交和 SHA-256；`scripts/build-predictions.py` 生成词库与只读 SQLite。
  `scripts/build-rime.sh host` 会调用该步骤并重新编译 Rime 数据。
- SQLite 文件 15,151,104 bytes（约 14.45 MiB），按索引查询，512 KiB 页缓存，
  关闭 mmap，不把整库或大模型载入内存。
- 无网络请求，不要求完全访问，不存储输入文本或新的英文个人词频。
  原有 Rime 学习仍在键盘私有目录内。
- `Resources/Prediction/manifest.json` 记录精确来源、校验值和计数；
  应用内开源许可可查看 rime-ice、SymSpell 和 prediction-data。
- GPL-3.0-only 的雾凇数据与 MIT 的 SymSpell 数据保留许可和原始来源说明。
  SymSpell 上游说明频率来自 Google Books Ngram 与 SCOWL；数据归属和链接见
  `Resources/Licenses/prediction-data-LICENSE.txt`。

## 验证

`bash scripts/test-predictions.sh` 覆盖英文上下文续词、纠错候选、大小写、句末、
中文后缀和缺失资源时的回退。450 次主机采样的英文与中文联合查询：
p50 0.22 ms、p95 0.95 ms；不代表 iPhone 真机延迟。

`scripts/test-rime.sh` 使用真实 librime 验证中文组合与提交，加入旧词库没有独立
条目的 `明天见`、`等一下`、`微信`、`朋友圈`、`语音输入`。
`testOfflineNextWordAndChineseContinuation` 在实际键盘中验证英文下一词、
中文续词不重复前缀、返回工具栏，以及关闭完全访问时的可用性。
