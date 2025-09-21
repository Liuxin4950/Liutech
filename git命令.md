
# Git 常用命令笔记

## 撤销与重置

### 场景 1：丢弃工作区未暂存的修改（让工作区和最后一次提交一致）
如果工作区有未 git add 的修改，想丢弃这些改动，恢复到最后一次提交的文件状态，执行：

```bash
git checkout .
```

这条命令会用暂存区（若暂存区是干净的，就是最后一次提交）的内容覆盖工作区，注意：未暂存的修改会被直接丢弃，谨慎操作。

> 注：在新版Git中，推荐使用 `git restore .` 来替代 `git checkout .`

### 场景 2：同时重置暂存区 + 工作区到最后一次提交
要是不仅工作区改了，还 git add 到暂存区了，想让暂存区和工作区都回到最后一次提交状态，执行：

```bash
git reset --hard HEAD
```

HEAD 指向当前分支最后一次提交，--hard 会强制重置 HEAD 指针、暂存区、工作区，让三者完全和最后一次提交一致，未提交的修改（不管是工作区还是暂存区）都会被清空，风险高，确认要丢弃改动再用。

### 场景 3：只撤销暂存区的修改，保留工作区的修改
如果你已经 git add 了，但想撤销暂存，同时保留工作区的修改：

```bash
git reset HEAD
```

或使用新版Git命令：

```bash
git restore --staged .
```

### 场景 4：撤销最近的一次或多次提交
如果你已经提交了，但想撤销最近的提交（保留修改）：

```bash
git reset --soft HEAD~1  # 撤销最近一次提交
git reset --soft HEAD~n  # 撤销最近n次提交
```

## 分支操作

### 创建与切换分支

```bash
# 创建新分支
git branch 分支名

# 切换到指定分支
git checkout 分支名

# 创建并切换到新分支（一步到位）
git checkout -b 分支名

# 新版Git创建并切换分支
git switch -c 分支名
```

### 分支合并

```bash
# 将指定分支合并到当前分支
git merge 分支名

# 使用rebase方式合并（使提交历史更线性）
git rebase 分支名
```

### 删除分支

```bash
# 删除已合并的分支
git branch -d 分支名

# 强制删除分支（即使未合并）
git branch -D 分支名
```

## 远程仓库操作

### 克隆仓库

```bash
git clone 仓库地址
```

### 拉取更新

```bash
# 拉取远程分支更新
git fetch

# 拉取并合并（相当于git fetch + git merge）
git pull

# 使用rebase方式拉取（保持提交历史线性）
git pull --rebase
```

### 推送更新

```bash
# 推送当前分支到远程
git push

# 推送本地分支到远程分支
git push origin 本地分支:远程分支

# 设置上游分支并推送
git push -u origin 分支名
```

## 查看状态与历史

### 查看状态

```bash
# 查看工作区和暂存区状态
git status

# 简洁模式查看状态
git status -s
```

### 查看提交历史

```bash
# 查看提交历史
git log

# 查看简洁的提交历史（一行显示）
git log --oneline

# 查看分支图
git log --graph --oneline

# 查看指定文件的修改历史
git log -p 文件路径
```

## 暂存工作区

```bash
# 暂存当前工作区的修改
git stash

# 暂存时添加说明
git stash save "暂存说明"

# 查看暂存列表
git stash list

# 应用最近的暂存（不删除暂存记录）
git stash apply

# 应用并删除最近的暂存
git stash pop

# 应用指定的暂存
git stash apply stash@{n}

# 删除所有暂存
git stash clear
```

## 标签管理

```bash
# 创建标签
git tag 标签名

# 创建带注释的标签
git tag -a 标签名 -m "标签说明"

# 查看所有标签
git tag

# 查看标签详情
git show 标签名

# 推送标签到远程
git push origin 标签名
# 推送所有标签
git push origin --tags
```

## 配置相关

```bash
# 设置全局用户名和邮箱
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"

# 查看配置
git config --list
```

## 高级技巧

### 修改最近一次提交

```bash
# 修改最近一次提交的信息或内容
git commit --amend
```

### 交互式rebase（重写历史）

```bash
# 交互式修改最近n次提交
git rebase -i HEAD~n
```

### 查找引入Bug的提交

```bash
# 二分查找定位问题提交
git bisect start
git bisect bad  # 标记当前版本有问题
git bisect good 提交ID  # 标记某个旧版本是好的
# Git会自动切换到中间版本，测试后标记：
git bisect good  # 或 git bisect bad
# 找到问题提交后结束查找
git bisect reset
```

### 清理未跟踪文件

```bash
# 查看将被删除的文件（预览）
git clean -n

# 删除未跟踪的文件
git clean -f

# 删除未跟踪的文件和目录
git clean -fd
```

希望这份完善后的Git命令笔记对你有帮助！
        