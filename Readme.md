# Api-X

JDK21

gradle run 调试运行
gradle createDistributable 打包


# TODO
加一个全局搜索的能力, Ctrl K 弹窗展示搜索输入框 焦点定位到输入框内, 输入内容后 在Request 名字和Body内模糊搜索内容, 搜索框下展示结果列表, 点击后跳转到对应的树内的元素, 关闭弹窗

> 性能专项
- 右侧相应区域的语法高亮问题
- sse响应的性能问题

> Request
request body 功能丰富
request body json高亮
- 支持更多参数格式, 表单, 多表单

删除一个Request 需要删掉整个 主目录下 这个request的文件夹, 并且需要二次确认.


Request 加Params Tab 功能和交互和Header是一样的, 但是区别是这里填的内容是 拼接在 URL后的, 例如这里配置了 k1:value1 k2:value2 两行, 在url就是 url?k1=value1&k2=value2

> 响应区域

响应区域 在 HTTP状态码同一行, 右侧加多一个 Copy 的icon, 复制响应的body到剪贴板
响应区域 在 HTTP状态码同一行, 右侧加多一个 Delete 的icon 删除所有的响应日志和压测bench日志

响应区域 默认展示Raw格式, 考虑性能问题, 需要手动切换到 格式化高亮形式
需要加一个 Request 的tab 展示实际发送请求的request内容, 因为左侧现在有变量和勾选的机制, 不知道实际的请求情况


> benchmark 功能

压测 策略还需要再确认 如何设计

响应区域 在 HTTP状态码同一行, 右侧加多一个 benchmark 的icon, 点击后弹窗出来, 窗口和设置页类似, 也是左右分隔, 左侧有这三行: 吞吐量, 稳定数, 稳定时, 然后吞吐量对应的右侧设置是 总量(整数输入框), 稳定数对应的右侧设置是 并发数, 总量, 稳定性对应的右侧设置是 并发数, 持续时间(整数 单位分钟)

执行压测
- 吞吐量 固定请求次数 一次性全部发起请求
- 稳定数 固定并发跑固定请求次数
- 稳定时 固定并发跑一段时间


request目录下的bench目录 存放的是: 压测配置json(参数: 并发数, 总次数, 结果报表: 所有RT值, P50 P75 P90 P99,最小 最大,平均 的RT统计值, QPS, TPS), 包含压测抛出来的的所有har


> 数据导入导出
导入和导出 Postman Collection V2.1 
描述，标签功能
Auth功能
Environment功能
- 变量使用 双括号做占位符例如  {{host}}
