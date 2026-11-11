// 顶层构建文件：只声明各模块共用的插件版本，apply false 表示这里不应用、由子模块应用
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
