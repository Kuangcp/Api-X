# Api-X

JDK21

gradle run 调试运行
gradle createDistributable 打包


# TODO

> 性能专项
- 右侧相应区域的语法高亮问题
- sse响应的性能问题

> Request
request body 功能丰富
request body json高亮 格式化

[X] 支持更多参数格式, 表单, 多表单

[X] 增加快捷键处理 Ctrl Tab 切换Request， 切换时需要弹出一个下拉列表，简洁的展示最近使用的Request，这样就能知道要按几下tab键了，也就是说需要记录最近的Request的打开查看时间，基于这个做倒排， 下拉列表展示Top30 

[X] Request 加Params Tab 功能和交互和Header是一样的, 但是区别是这里填的内容是 拼接在 URL后的, 例如这里配置了 k1:value1 k2:value2 两行, 在url就是 url?k1=value1&k2=value2

> 响应区域

[X] 响应区域 默认展示Raw格式, 考虑性能问题, 需要手动切换到 格式化高亮形式
[X] 需要加一个 Request 的tab 展示实际发送请求的request内容, 因为左侧现在有变量和勾选的机制, 不知道实际的请求情况


> benchmark 功能

压测 策略还需要再确认 如何设计

响应区域 在 HTTP状态码同一行, 右侧加多一个 benchmark 的icon, 点击后弹窗出来, 窗口和设置页类似, 也是左右分隔, 左侧有这三行: 吞吐量, 稳定数, 稳定时, 然后吞吐量对应的右侧设置是 总量(整数输入框), 稳定数对应的右侧设置是 并发数, 总量, 稳定性对应的右侧设置是 并发数, 持续时间(整数 单位分钟)

执行压测
- 吞吐量 固定请求次数 一次性全部发起请求
- 稳定数 固定并发跑固定请求次数
- 稳定时 固定并发跑一段时间


request目录下的bench目录 存放的是: 压测配置json(参数: 并发数, 总次数, 结果报表: 所有RT值, P50 P75 P90 P99,最小 最大,平均 的RT统计值, QPS, TPS), 包含压测抛出来的的所有har


