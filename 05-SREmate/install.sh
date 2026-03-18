#!/bin/bash

# SREmate卸载脚本
# 移除全局命令

echo "正在卸载SREmate..."
rm -f /usr/local/bin/sremate

echo "✓ SREmate已卸载"


# SREmate安装脚本
# 将sremate命令注册为全局命令

# 设置Java 21环境
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

echo "开始构建SREmate..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "构建失败，请检查错误信息"
    exit 1
fi
JAR_PATH="$(pwd)/target/SREmate-1.0-SNAPSHOT.jar"
INSTALL_DIR="$HOME/bin"
mkdir -p "$INSTALL_DIR"

echo "正在安装sremate命令到 $INSTALL_DIR ..."

# 创建启动脚本
cat > "$INSTALL_DIR/sremate" << EOF
#!/bin/bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export PATH=\$JAVA_HOME/bin:\$PATH
java -jar "$JAR_PATH" "\$@"
EOF

# 添加执行权限
chmod +x "$INSTALL_DIR/sremate"

echo "✓ SREmate安装完成！"
echo "✓ 现在可以在任何目录下运行 'sremate' 命令"

# 若 ~/bin 不在 PATH 中，自动写入 ~/.zshrc
if ! grep -q 'HOME/bin' ~/.zshrc 2>/dev/null; then
    echo 'export PATH=$HOME/bin:$PATH' >> ~/.zshrc
    echo "✓ 已将 ~/bin 写入 ~/.zshrc"
fi
export PATH="$HOME/bin:$PATH"
echo "✓ 当前 shell PATH 已生效，现在可直接运行 'sremate'"
echo ""
echo "⚠️  注意：请用 'source install.sh' 而非 'bash install.sh' 执行本脚本"
echo "   否则 PATH 修改只在子进程生效，当前终端无法识别 sremate 命令"
