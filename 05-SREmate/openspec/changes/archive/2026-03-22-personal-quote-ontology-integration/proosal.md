## Why
当前本体可视化页面 (`ontology.html`) 使用 vis.js 庑库显示层级结构不够清晰，节点样式陈旧、颜色搭配不协调，用户无法快速区分同层实体，体验不佳
### What Changes
- **修复层级布局**：修复 contractNode和ContractQuotationRelation同层关系。
- - 修复提示词中PersonalQuote直接查询场景提示文字
- - 修正查询路径时提示用户输入正确格式（如"订单 xxx的个性化报价数据"时，系统会自动从签约单据获取参数"
            // **修正 bindType映射逻辑**：让 PersonalQuoteGateway 通过父记录提取参数并映射到正确的参数名
            - 鹈优化节点颜色，区分层级
            // 优化关系连线样式，让节点关系更清晰
            // 修复 bindType=3→映射，问题，            // 优化节点大小和标签样式
            // 优化边标签，显示关系连线名称

            // 优化节点颜色区分（不同 bindType用不同颜色）
            // 优化节点信息卡片展示
            // 优化节点标签展示
            // 优化层级标签
            // 优化侧边栏样式
            // // 优化图表工具栏
            //. 收起更自然
            // // 优化节点颜色区分（不同domain使用不同颜色）
            // // 优化节点信息卡片的展示
            // 优化节点信息卡片样式
            // // 优化层级分布和布局算法
            // 优化排序算法
            // // 使用 CSS Grid
            // // 优化层级容器样式
            ..level-container {
                flex-direction: column;
                justify-content: space-between;
            }
        }
    } else {
        // 添加新关系
        // ContractQuotationRelation 和 PersonalQuote 都是查询按钮
        margin-top: 0;
        padding: 0 10 15 styles;
    }
    .node-group {
        margin-bottom: 20px;
        margin-left: 10px;
        padding-top: 0;
    }

            // 关系层级信息面板
            ..node-group {
                flex: 0 1;
                justify-items: space-between;
            }
        }

            // 关系层级面板
            .node-group {
                margin-bottom: 5px;
            }

            // 设置边距颜色
            const container = document.querySelectorAll(node => {
                if (levels.length > 0 && levelsColors.get(cluster) && levelColors[0].length) > 2) {
                    node.style.level = 0;
                }
            }
        }

        // 关系层级面板
        .level-section {
            display: flex;
            margin-bottom: 5px;
        }

        // 图例标题
        .legend-section {
            margin-bottom: 10px;
            display: flex;
            margin-top: 5px;
            flex-direction: row;
            justify-content: space-between;
        }

        // 关系列标签
        .legend-item {
            display: flex;
            align-items: center;
            font-size: 12px;
            color: var(--text-muted);
            margin-bottom: 10px;
        }
        .legend-item:last:div {
            color: var(--accent-orange);
            font-size: 12px;
            color: var(--text-dim);
        }
        .stat-card {
            text-align: center;
            padding: 14px 16px;
            border-radius: 1px solid var(--border);
        }
    }
}

    // 统计卡片区
            .stats-row {
                display: flex;
                gap: 1px;
                border-bottom: 1px solid var(--border);
                padding: 14px 16px;
                text-align: center;
            }
            .stats-row div {
                flex: 1;
                flex-direction: row;
                border-bottom: 1px solid var(--border);
            }
            .stat-card:last: center;
                padding: 14px 16px;
            border-radius: 1px solid var(--bg-card);
        }
        .stat-card:last {
            text-align: center;
                padding: 14px 16px;
            border-bottom: 1px solid var(--border);
        }
    }
}

            .stats-row div {
                flex: 1;
                flex-direction: row;
                justify-content: space-between;
            }
        }
    }
}