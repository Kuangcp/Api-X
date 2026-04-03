# Api-X

JDK21

gradle run 调试运行
gradle createDistributable 打包


# TODO
加一个全局搜索的能力, Ctrl K 弹窗展示搜索输入框 焦点定位到输入框内, 输入内容后 在Request 名字和Body内模糊搜索内容, 搜索框下展示结果列表, 点击后跳转到对应的树内的元素, 关闭弹窗

响应内容需要跟随不同的Request 支持切换, 每个Request需要保存最近10次响应的完整内容, 切换Request时加载这个Request的最后一次响应内容渲染出来, 而旧的Request的响应内容绘制出来的组件和内存就可以销毁掉了, 节省内存. 
因为一般内容会很大, 所以走文件的缓存(放在 应用数据目录的 新建一个目录 req-log 下 按请求id平铺建目录 每次响应都是一个文件), 不存数据库


