# ElasticSearch plugin to enable Garbage Collector Control Interceptor (GCI)

Tune GC to decrease tail latency is hard. It requires a lot of knowledge of the application and JVM internals, particularly
the garbage collector. Trying to improve life of ES users, the ES dev team team has proposed some [expert flags](https://github.com/elastic/elasticsearch/blob/4073349267adcc8147638b531299001713b58ee0/distribution/src/main/resources/config/jvm.options#L25-L43).
Even though this is a great contribution, it might not be enough for some use-cases. Furthermore, it is hard for the dev team to keep up with JVM changes and whatnot.

To help on that, as part of my PhD work, I am proposing and evaluating an ES action filter that uses with [GCI](https://github.com/danielfireman/gci).
Out main goal is to use flow control (or load shedding) to drastically decrease long tail latency. It is also a goal
to have minimum (or neglible) impact on system's throughput, mean latency and CPU usage.

**Feeling adventurious? Want to give it a try? Please let me know!**

**Documentation and results are going to be available soon!**
