#!/bin/bash

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

JAR_PATH="$(pwd)/target/05-SREmate-1.0-SNAPSHOT.jar"
INSTALL_DIR="/usr/local/bin"

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
