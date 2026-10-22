# 本地语音资源来源

资源由 `scripts/bootstrap-local-speech.sh` 下载、校验并复制。二进制在忽略目录中，不将下载一半的文件作为产品资源。主 App 打包模型；键盘不链接 LocalSpeech。

## 固定资源

模型仓库：`csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17`。

固定 revision：`2365baeacb507f821a0c8120fcee3d484dba7a07`。

| 文件 | SHA-256 |
| --- | --- |
| `model.int8.onnx`，239,233,841 bytes | `c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51` |
| `tokens.txt` | `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc` |
| 公共测试夹具 `test_wavs/zh.wav` | `b77f1794fe374a0ba1ee1dc458bfaf9349496cbbfc32780c50ba3c5a7ad8e373` |

模型与 tokens 放入 `Resources/SenseVoice/`；公开夹具放入 `AppTests/Fixtures/`，不包含用户录音。HF 固定 revision 链接：

`https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tree/2365baeacb507f821a0c8120fcee3d484dba7a07`

| 运行时 | 上游地址 | 压缩包 SHA-256 |
| --- | --- | --- |
| sherpa-onnx 1.13.8，Apache-2.0 | `https://github.com/k2-fsa/sherpa-onnx/releases/download/xcframework/sherpa-onnx-v1.13.8-ios-static.xcframework.zip` | `6b8e769cb153343270fdccbe92e3b3db0d1c421d67fa0989ab01fdf5b2fcf2de` |
| ONNX Runtime 1.28.2，MIT | `https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-ios-static-xcframework-1.28.2.xcframework.zip` | `2c2299acbb461d26d4bac4bc85985d40e7c7177ed6072703ae0846d88b0b4599` |

两个 xcframework 包含 device／simulator 切片。许可在 `Resources/Licenses/`。模型／夹具的完整授权和 notices 仍应在公开分发前按固定模型仓库核对，不把“公开下载”当作任意再分发授权。

## 验证口径

`SenseVoiceTests` 运行打包 int8 模型、读取公共 16 kHz mono PCM，检查非空中文输出，并验证预先取消不会启动模型。两项测试通过，没有打开麦克风。

没有为夹具建立人工 gold transcript。最初的测试误用了与音频无关的预期词，导致真实解码后断言失败；现改为明确命名的集成 smoke，不作为准确率测试。已观测输出为“开饭时间早上9点至下午5点。”，不能据此宣称字错率或真实人声体验。

桌面新分支的 TextStream 是经 Mac MLX 子进程运行的 Qwen3-ASR，不是可直接移入 iOS 的适配器。此次使用真正的 iOS C API 运行时，没有包装桌面 subprocess，也没有引入竞品服务或提供商密钥。
