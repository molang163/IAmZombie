# I Am Zombie? 1.1.3

[English](#english) | [中文](#中文)

## English

Version 1.1.3 adds separately packaged builds for Minecraft 1.21.8, 1.21.10,
1.21.11, 26.1, and 26.2. Download the JAR whose `+mc...` suffix matches your
Minecraft version. Gameplay, saved data, configuration formats, and protocol
schemas are unchanged.

### Builds and Java requirements

| Minecraft | File | Java |
|---|---|---|
| 1.21.8 | `iamzombieq-1.1.3+mc1.21.8.jar` | Java 22 or 25 |
| 1.21.10 | `iamzombieq-1.1.3+mc1.21.10.jar` | Java 22 or 25 |
| 1.21.11 | `iamzombieq-1.1.3+mc1.21.11.jar` | Java 25 |
| 26.1 | `iamzombieq-1.1.3+mc26.1.jar` | Java 25 |
| 26.2 | `iamzombieq-1.1.3+mc26.2.jar` | Java 25 |

Install only the file matching your Minecraft version.

### Fixes

- Fixed a client crash during the configuration stage on older supported
  Minecraft versions.
- Restored Herobrine right-click interactions on pre-26.2 NeoForge without
  duplicate handling; 26.2 retains its merged interaction-event path.
- Retains the Windows NTFS configuration-migration fix, including its
  fail-closed identity and namespace checks.

## 中文

1.1.3 为 Minecraft 1.21.8、1.21.10、1.21.11、26.1 和 26.2 分别提供独立构建。
请下载文件名中 `+mc...` 后缀与 Minecraft 版本一致的 JAR。本次不改变玩法、存档数据、
配置格式或协议 schema。

### 构建与 Java 要求

| Minecraft | 文件 | Java |
|---|---|---|
| 1.21.8 | `iamzombieq-1.1.3+mc1.21.8.jar` | Java 22 或 25 |
| 1.21.10 | `iamzombieq-1.1.3+mc1.21.10.jar` | Java 22 或 25 |
| 1.21.11 | `iamzombieq-1.1.3+mc1.21.11.jar` | Java 25 |
| 26.1 | `iamzombieq-1.1.3+mc26.1.jar` | Java 25 |
| 26.2 | `iamzombieq-1.1.3+mc26.2.jar` | Java 25 |

请只安装与你的 Minecraft 版本对应的文件。

### 修复

- 修复旧版 Minecraft 客户端在配置阶段的启动崩溃。
- 恢复 NeoForge 26.2 之前版本的 Herobrine 右键交互并避免重复处理；26.2 继续使用合并后的
  交互事件路径。
- 完整保留 Windows NTFS 配置迁移修复及其失败关闭的身份和命名空间检查。
