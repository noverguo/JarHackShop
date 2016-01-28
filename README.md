# JarHackShop
- 此工具集目前只包含一个工具SimpilyMethodTool，其思路来自于[simplify][simplify]中的模拟执行。与[simplify][simplify]不一样的是，[simplify][simplify]是基于smali，且在android中模拟执行，不但速度非常非常慢，而且Bug比较多（其demo测试成功，但用真实的smali总报错）。SimpilyMethodTool思路是抽取出简单的方法，并把它保存起来，然后把全部参数为常量（或无参）且调用到这些方法的地方模拟调用（通过Java虚拟机），最后把结果进行替换，以达到简化方法的作用，这对于代码压缩与反混淆都有一定的帮助。

[simplify]: https://github.com/CalebFenton/simplify
