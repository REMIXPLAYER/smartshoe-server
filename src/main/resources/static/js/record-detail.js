/**
 * SmartShoe Admin - Record Detail Page JavaScript
 */

/* global Chart */

(function() {
    var recordId = window.recordId || '';
    var rawSensorData = []; // 原始完整数据
    var sensorData = []; // 当前渲染的数据（可能经过降采样）
    var sensorChart = null;
    var distributionChart = null;
    var rangeChart = null;

    // 降采样配置
    const DEFAULT_MAX_POINTS = 100; // 默认最大显示点数
    var currentMaxPoints = DEFAULT_MAX_POINTS; // 当前设置的最大点数

    /**
     * 改进的LTTB (Largest Triangle Three Buckets) 降采样算法
     * 针对三个传感器数据分别计算重要性，保留更准确的特征点
     */
    function lttbDownsample(data, threshold) {
        if (data.length <= threshold) return data;

        const sampled = [];
        let sampledIndex = 0;

        // 桶大小
        const bucketSize = (data.length - 2) / (threshold - 2);

        // 保留第一个点
        sampled[sampledIndex++] = data[0];

        for (let i = 0; i < threshold - 2; i++) {
            const bucketStart = Math.floor((i + 1) * bucketSize) + 1;
            const bucketEnd = Math.floor((i + 2) * bucketSize) + 1;
            const bucket = data.slice(bucketStart, bucketEnd);

            // 计算当前桶的平均点
            const avgX = bucketStart + (bucketEnd - bucketStart) / 2;
            const avgPoint = {
                sensor1: bucket.reduce((sum, p) => sum + p.sensor1, 0) / bucket.length,
                sensor2: bucket.reduce((sum, p) => sum + p.sensor2, 0) / bucket.length,
                sensor3: bucket.reduce((sum, p) => sum + p.sensor3, 0) / bucket.length
            };

            // 找到桶中最重要的点（综合三个传感器的变化）
            let maxImportance = -1;
            let selectedPoint = data[bucketStart];

            for (let j = 0; j < bucket.length; j++) {
                const point = bucket[j];

                // 计算该点与前后点的综合变化程度
                const importance =
                    Math.abs(point.sensor1 - avgPoint.sensor1) +
                    Math.abs(point.sensor2 - avgPoint.sensor2) +
                    Math.abs(point.sensor3 - avgPoint.sensor3);

                if (importance > maxImportance) {
                    maxImportance = importance;
                    selectedPoint = point;
                }
            }

            sampled[sampledIndex++] = selectedPoint;
        }

        // 保留最后一个点
        sampled[sampledIndex++] = data[data.length - 1];

        return sampled;
    }

    /**
     * 简单的均匀降采样（用于极速模式）
     */
    function uniformDownsample(data, threshold) {
        if (data.length <= threshold) return data;

        const sampled = [];
        const step = data.length / threshold;

        for (let i = 0; i < threshold; i++) {
            const index = Math.floor(i * step);
            sampled.push(data[index]);
        }

        // 确保包含最后一个点
        if (sampled[sampled.length - 1] !== data[data.length - 1]) {
            sampled.push(data[data.length - 1]);
        }

        return sampled;
    }

    /**
     * 根据用户选择获取降采样后的数据
     * 优化：调整各模式的实际显示点数，避免卡顿
     */
    function getDownsampledData(data, maxPoints) {
        if (maxPoints === 0 || data.length <= maxPoints) {
            return data;
        }

        // 优化：根据选择的模式调整实际采样点数
        var targetPoints;
        if (maxPoints === 500) {
            // 500 点模式：实际只采样 300 点，配合后续聚合，最终显示约 100-150 点
            targetPoints = 300;
        } else if (maxPoints === 200) {
            // 200 点模式：保持原样
            targetPoints = 200;
        } else if (maxPoints === 100) {
            // 100 点模式：保持原样
            targetPoints = 100;
        } else {
            // 全部数据模式：根据数据量动态调整
            if (data.length > 5000) {
                targetPoints = 500; // 超大数据量限制在 500 点
            } else {
                targetPoints = maxPoints;
            }
        }

        // 根据点数选择算法
        if (targetPoints <= 200) {
            // 少量点使用均匀采样（更快）
            return uniformDownsample(data, targetPoints);
        } else {
            // 较多点使用 LTTB（保留形状更好）
            return lttbDownsample(data, targetPoints);
        }
    }

    /**
     * 渲染点数选择变化处理
     */
    window.onPointCountChange = function() {
        const select = document.getElementById('pointCountSelect');
        const selectedValue = parseInt(select.value);
        currentMaxPoints = selectedValue === 0 ? rawSensorData.length : selectedValue;

        console.log('切换渲染点数: ' + currentMaxPoints + ' (原始数据: ' + rawSensorData.length + ')');

        // 重新降采样并渲染
        sensorData = getDownsampledData(rawSensorData, currentMaxPoints);

        // 更新数据显示信息
        updateDataInfo();

        // 销毁旧图表并重新渲染
        if (sensorChart) {
            sensorChart.destroy();
            sensorChart = null;
        }
        renderSensorChart();
    };

    /**
     * 更新数据信息显示
     */
    function updateDataInfo() {
        const info = document.getElementById('dataInfo');
        if (rawSensorData.length > 0) {
            const ratio = Math.round((sensorData.length / rawSensorData.length) * 100);
            info.textContent = `显示 ${sensorData.length}/${rawSensorData.length} (${ratio}%)`;
        }
    }

    async function loadSensorData() {
        try {
            var response = await fetch('/admin/api/records/' + recordId + '/data');
            var data = await response.json();

            if (data.success === false) {
                console.error('Failed to load data:', data.message);
                return;
            }

            // 保存原始完整数据
            rawSensorData = data;
            console.log('加载原始数据: ' + rawSensorData.length + ' 点');

            // 根据当前设置进行降采样
            sensorData = getDownsampledData(rawSensorData, currentMaxPoints);

            // 更新数据信息显示
            updateDataInfo();

            renderCharts();
            calculateStats();
        } catch (error) {
            console.error('Error loading sensor data:', error);
        }
    }

    function renderCharts() {
        renderSensorChart();
        renderDistributionChart();
        renderRangeChart();
    }

    /**
     * 数据聚合 - 将密集的点聚合成区域块（min-max范围）
     * 用于优化大数据量的渲染性能
     */
    function aggregateData(data, aggregationFactor) {
        if (data.length <= 200 || aggregationFactor <= 1) {
            return data.map(d => ({
                timestamp: d.timestamp,
                sensor1: d.sensor1,
                sensor2: d.sensor2,
                sensor3: d.sensor3,
                isAggregated: false
            }));
        }

        const aggregated = [];
        // 修复：正确计算桶大小 - 每N个点聚合成1个
        const bucketSize = aggregationFactor;

        for (let i = 0; i < data.length; i += bucketSize) {
            const bucket = data.slice(i, Math.min(i + bucketSize, data.length));

            if (bucket.length === 1) {
                aggregated.push({
                    timestamp: bucket[0].timestamp,
                    sensor1: bucket[0].sensor1,
                    sensor2: bucket[0].sensor2,
                    sensor3: bucket[0].sensor3,
                    isAggregated: false
                });
            } else {
                // 计算每个传感器的min, max, avg
                const stats = {
                    sensor1: { min: Infinity, max: -Infinity, sum: 0 },
                    sensor2: { min: Infinity, max: -Infinity, sum: 0 },
                    sensor3: { min: Infinity, max: -Infinity, sum: 0 }
                };

                bucket.forEach(d => {
                    stats.sensor1.min = Math.min(stats.sensor1.min, d.sensor1);
                    stats.sensor1.max = Math.max(stats.sensor1.max, d.sensor1);
                    stats.sensor1.sum += d.sensor1;

                    stats.sensor2.min = Math.min(stats.sensor2.min, d.sensor2);
                    stats.sensor2.max = Math.max(stats.sensor2.max, d.sensor2);
                    stats.sensor2.sum += d.sensor2;

                    stats.sensor3.min = Math.min(stats.sensor3.min, d.sensor3);
                    stats.sensor3.max = Math.max(stats.sensor3.max, d.sensor3);
                    stats.sensor3.sum += d.sensor3;
                });

                const count = bucket.length;
                const midIndex = Math.floor(bucket.length / 2);

                // 创建聚合点 - 使用中间点的时间戳，但包含范围信息
                aggregated.push({
                    timestamp: bucket[midIndex].timestamp,
                    sensor1: Math.round(stats.sensor1.sum / count),
                    sensor2: Math.round(stats.sensor2.sum / count),
                    sensor3: Math.round(stats.sensor3.sum / count),
                    sensor1Range: [stats.sensor1.min, stats.sensor1.max],
                    sensor2Range: [stats.sensor2.min, stats.sensor2.max],
                    sensor3Range: [stats.sensor3.min, stats.sensor3.max],
                    count: count,
                    isAggregated: true
                });
            }
        }

        return aggregated;
    }

    function renderSensorChart() {
        var canvas = document.getElementById('sensorChart');
        if (!canvas) {
            console.error('找不到图表画布元素');
            return;
        }

        var ctx = canvas.getContext('2d');

        // 如果已有图表，先销毁
        if (sensorChart) {
            sensorChart.destroy();
            sensorChart = null;
        }

        // 根据数据量决定是否进行聚合
        // 注意：sensorData 已经是降采样后的数据（100/200/500/全部）
        // 优化：更激进的聚合策略，避免卡顿
        var aggregationFactor = 1;
        if (sensorData.length > 2000) {
            // 全部数据模式：激进聚合，保持显示约 300-400 点
            aggregationFactor = Math.ceil(sensorData.length / 350);
        } else if (sensorData.length > 800) {
            // 500 点模式：轻微聚合，保持显示约 250-300 点
            aggregationFactor = 3; // 3 个点聚合成 1 个
        } else if (sensorData.length > 400) {
            // 200 点模式：不聚合
            aggregationFactor = 1;
        }
        // 100 点模式：不聚合

        var displayData = aggregationFactor > 1 ? aggregateData(sensorData, aggregationFactor) : sensorData;

        // 调试信息
        console.log('渲染图表:');
        console.log('- 原始数据点数:', sensorData.length);
        console.log('- 聚合因子:', aggregationFactor);
        console.log('- 显示数据点数:', displayData.length);
        console.log('- 第一个数据点:', displayData[0]);
        console.log('- 最后一个数据点:', displayData[displayData.length - 1]);

        if (aggregationFactor > 1) {
            console.log(`数据聚合: ${sensorData.length} 点 → ${displayData.length} 点 (聚合因子: ${aggregationFactor})`);
        }

        // 检查数据有效性
        if (!displayData || displayData.length === 0) {
            console.error('没有数据可供渲染');
            return;
        }

        var labels = displayData.map(function(d, i) {
            var time = new Date(d.timestamp);
            return time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        });

        var sensor1Data = displayData.map(function(d) { return d.sensor1; });
        var sensor2Data = displayData.map(function(d) { return d.sensor2; });
        var sensor3Data = displayData.map(function(d) { return d.sensor3; });

        console.log('传感器1数据范围:', Math.min(...sensor1Data), '-', Math.max(...sensor1Data));
        console.log('传感器2数据范围:', Math.min(...sensor2Data), '-', Math.max(...sensor2Data));
        console.log('传感器3数据范围:', Math.min(...sensor3Data), '-', Math.max(...sensor3Data));

        var datasets = [
            {
                label: '传感器1 (前掌)',
                data: sensor1Data,
                borderColor: '#7AAACE',
                backgroundColor: 'rgba(122, 170, 206, 0.1)',
                fill: false, // 改为false，避免填充影响性能
                tension: 0.3,
                pointRadius: 0,
                pointHoverRadius: 4,
                borderWidth: 2
            },
            {
                label: '传感器2 (足弓)',
                data: sensor2Data,
                borderColor: '#355872',
                backgroundColor: 'rgba(53, 88, 114, 0.1)',
                fill: false,
                tension: 0.3,
                pointRadius: 0,
                pointHoverRadius: 4,
                borderWidth: 2
            },
            {
                label: '传感器3 (脚跟)',
                data: sensor3Data,
                borderColor: '#9CD5FF',
                backgroundColor: 'rgba(156, 213, 255, 0.1)',
                fill: false,
                tension: 0.3,
                pointRadius: 0,
                pointHoverRadius: 4,
                borderWidth: 2
            }
        ];

        sensorChart = new Chart(ctx, {
            type: 'line',
            data: { labels: labels, datasets: datasets },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'nearest',
                    intersect: false,
                    axis: 'x'
                },
                // 性能优化：减少动画和渲染开销
                animation: false, // 完全禁用动画
                transitions: {
                    active: {
                        animation: {
                            duration: 0
                        }
                    }
                },
                // 注意：不能同时使用 parsing: false 和 labels 数组
                // parsing: false, // 禁用 - 因为我们使用 labels 数组
                normalized: true, // 启用归一化，提高渲染性能
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(44, 44, 44, 0.9)',
                        titleColor: '#fff',
                        bodyColor: '#fff',
                        padding: 10,
                        cornerRadius: 6,
                        // 性能优化：限制tooltip显示
                        mode: 'nearest',
                        intersect: false,
                        // 大数据量时禁用tooltip以提高性能
                        enabled: displayData.length <= 500
                    },
                    // 禁用decimation插件，因为我们已经手动聚合了数据
                    decimation: {
                        enabled: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        max: 4095,
                        title: {
                            display: true,
                            text: '压力值'
                        },
                        // 性能优化
                        ticks: {
                            maxTicksLimit: 8
                        }
                    },
                    x: {
                        title: {
                            display: true,
                            text: '时间'
                        },
                        ticks: {
                            maxTicksLimit: window.innerWidth < 576 ? 4 : 8,
                            maxRotation: window.innerWidth < 576 ? 45 : 0,
                            // 性能优化：自动跳过标签
                            autoSkip: true,
                            autoSkipPadding: 20
                        },
                        // 性能优化
                        grid: {
                            drawBorder: true,
                            display: true
                        }
                    }
                },
                // 元素渲染优化
                elements: {
                    line: {
                        borderWidth: 2,
                        tension: 0.2, // 降低曲线平滑度以提高性能
                        borderJoinStyle: 'round',
                        capBezierPoints: false, // 禁用贝塞尔点计算
                        showLine: true // 确保显示线条
                    },
                    point: {
                        radius: displayData.length > 200 ? 0 : 2, // 大数据量时隐藏点
                        hitRadius: 8,
                        hoverRadius: 4
                    }
                },
                // 布局优化
                layout: {
                    padding: {
                        left: 5,
                        right: 5,
                        top: 5,
                        bottom: 5
                    }
                },
                // 性能优化：禁用不必要的选项
                spanGaps: true
            }
        });
    }

    function renderDistributionChart() {
        var ctx = document.getElementById('distributionChart');
        if (!ctx) return;
        
        var avg1 = sensorData.reduce(function(sum, d) { return sum + d.sensor1; }, 0) / sensorData.length;
        var avg2 = sensorData.reduce(function(sum, d) { return sum + d.sensor2; }, 0) / sensorData.length;
        var avg3 = sensorData.reduce(function(sum, d) { return sum + d.sensor3; }, 0) / sensorData.length;

        distributionChart = new Chart(ctx.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: ['传感器1 (前掌)', '传感器2 (足弓)', '传感器3 (脚跟)'],
                datasets: [{
                    data: [avg1, avg2, avg3],
                    backgroundColor: ['#7AAACE', '#355872', '#9CD5FF'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 15,
                            usePointStyle: true
                        }
                    }
                }
            }
        });
    }

    function renderRangeChart() {
        var ctx = document.getElementById('rangeChart');
        if (!ctx) return;
        
        var ranges = [
            { label: '0-1000', count: 0 },
            { label: '1000-2000', count: 0 },
            { label: '2000-3000', count: 0 },
            { label: '3000-4095', count: 0 }
        ];

        sensorData.forEach(function(d) {
            var avg = (d.sensor1 + d.sensor2 + d.sensor3) / 3;
            if (avg < 1000) ranges[0].count++;
            else if (avg < 2000) ranges[1].count++;
            else if (avg < 3000) ranges[2].count++;
            else ranges[3].count++;
        });

        rangeChart = new Chart(ctx.getContext('2d'), {
            type: 'bar',
            data: {
                labels: ranges.map(function(r) { return r.label; }),
                datasets: [{
                    label: '数据点数',
                    data: ranges.map(function(r) { return r.count; }),
                    backgroundColor: ['#7AAACE', '#355872', '#9CD5FF', '#5B9BD5'],
                    borderRadius: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: { stepSize: 1 }
                    }
                }
            }
        });
    }

    function calculateStats() {
        var avg1 = Math.round(sensorData.reduce(function(sum, d) { return sum + d.sensor1; }, 0) / sensorData.length);
        var avg2 = Math.round(sensorData.reduce(function(sum, d) { return sum + d.sensor2; }, 0) / sensorData.length);
        var avg3 = Math.round(sensorData.reduce(function(sum, d) { return sum + d.sensor3; }, 0) / sensorData.length);

        var sensor1Avg = document.getElementById('sensor1Avg');
        var sensor2Avg = document.getElementById('sensor2Avg');
        var sensor3Avg = document.getElementById('sensor3Avg');
        
        if (sensor1Avg) sensor1Avg.textContent = avg1;
        if (sensor2Avg) sensor2Avg.textContent = avg2;
        if (sensor3Avg) sensor3Avg.textContent = avg3;

        if (sensorData.length > 1) {
            var firstTimestamp = sensorData[0].timestamp;
            var lastTimestamp = sensorData[sensorData.length - 1].timestamp;
            
            var durationMs = lastTimestamp - firstTimestamp;
            var durationSec = durationMs / 1000;
            
            var durationEl = document.getElementById('duration');
            if (durationEl) {
                if (durationSec < 0) {
                    durationEl.textContent = '计算错误';
                } else {
                    var minutes = Math.floor(durationSec / 60);
                    var seconds = Math.round(durationSec % 60);
                    
                    if (minutes > 0) {
                        durationEl.textContent = minutes + '分' + seconds + '秒';
                    } else {
                        durationEl.textContent = seconds + '秒';
                    }
                }
            }
        } else if (sensorData.length === 1) {
            var durationEl = document.getElementById('duration');
            if (durationEl) durationEl.textContent = '0秒';
        }
    }

    window.toggleSensor = function(index) {
        if (sensorChart) {
            var meta = sensorChart.getDatasetMeta(index);
            meta.hidden = !meta.hidden;
            sensorChart.update();

            // Update button visual state
            var btn = document.querySelector('.sensor-legend-btn[data-index="' + index + '"]');
            if (meta.hidden) {
                btn.classList.remove('active');
                btn.classList.add('hidden');
            } else {
                btn.classList.add('active');
                btn.classList.remove('hidden');
            }
        }
    };

    window.exportData = function() {
        var dataStr = JSON.stringify(sensorData, null, 2);
        var blob = new Blob([dataStr], { type: 'application/json' });
        downloadFile(blob, 'sensor_data_' + recordId + '.json');
    };

    window.exportCSV = function() {
        var csv = 'Timestamp,Sensor1,Sensor2,Sensor3\n';
        sensorData.forEach(function(d) {
            csv += d.timestamp + ',' + d.sensor1 + ',' + d.sensor2 + ',' + d.sensor3 + '\n';
        });
        var blob = new Blob([csv], { type: 'text/csv' });
        downloadFile(blob, 'sensor_data_' + recordId + '.csv');
    };

    window.delRecord = function(btn) {
        var id = btn.getAttribute('data-id');
        if (!confirm('确定要删除这条数据记录吗？')) return;
        
        fetch('/admin/api/records/' + id + '/delete', {method: 'POST'})
        .then(function(r) { return r.json(); })
        .then(function(data) {
            alert(data.message);
            if (data.success) window.location.href = '/admin/records';
        });
    };

    // Load data on page load
    if (recordId) {
        loadSensorData();
    }
})();
