/**
 * SmartShoe Admin - Charts Page JavaScript
 */

/* global Chart */

(function() {
    let uploadTrendChart, userActivityChart, storageChart, compressionChart, hourlyChart;
    let currentDays = 7;

    function initCharts() {
        initUploadTrendChart();
        initUserActivityChart();
        initStorageChart();
        initCompressionChart();
        initHourlyChart();
    }

    function initUploadTrendChart() {
        const ctx = document.getElementById('uploadTrendChart');
        if (!ctx) return;
        
        uploadTrendChart = new Chart(ctx.getContext('2d'), {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: '记录数',
                    data: [],
                    borderColor: '#355872',
                    backgroundColor: 'rgba(53, 88, 114, 0.1)',
                    fill: true,
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
            }
        });
    }

    function initUserActivityChart() {
        const ctx = document.getElementById('userActivityChart');
        if (!ctx) return;
        
        userActivityChart = new Chart(ctx.getContext('2d'), {
            type: 'bar',
            data: {
                labels: [],
                datasets: [{
                    label: '活跃用户数',
                    data: [],
                    backgroundColor: '#7AAACE',
                    borderRadius: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
            }
        });
    }

    function initStorageChart() {
        const ctx = document.getElementById('storageChart');
        if (!ctx) return;
        
        storageChart = new Chart(ctx.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: ['未压缩数据', '已压缩数据'],
                datasets: [{
                    data: [0, 0],
                    backgroundColor: ['#9CD5FF', '#355872'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { 
                    legend: { position: 'bottom' },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const label = context.label || '';
                                const value = context.raw || 0;
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : 0;
                                return label + ': ' + formatBytes(value) + ' (' + percentage + '%)';
                            }
                        }
                    }
                }
            }
        });
    }

    function initCompressionChart() {
        const ctx = document.getElementById('compressionChart');
        if (!ctx) return;
        
        compressionChart = new Chart(ctx.getContext('2d'), {
            type: 'bar',
            data: {
                labels: ['< 50%', '50-60%', '60-70%', '70-80%', '> 80%'],
                datasets: [{
                    label: '记录数',
                    data: [0, 0, 0, 0, 0],
                    backgroundColor: ['#E74C3C', '#F39C12', '#F1C40F', '#7AAACE', '#27AE60'],
                    borderRadius: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
            }
        });
    }

    function initHourlyChart() {
        const ctx = document.getElementById('hourlyChart');
        if (!ctx) return;
        
        const labels = [];
        for (let i = 0; i < 24; i++) {
            labels.push(i + ':00');
        }
        hourlyChart = new Chart(ctx.getContext('2d'), {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: '上传次数',
                    data: new Array(24).fill(0),
                    backgroundColor: '#7AAACE',
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
            }
        });
    }

    async function loadAllData() {
        await Promise.all([
            loadOverviewStats(),
            loadUploadTrend(),
            loadUserActivity(),
            loadStorageStats(),
            loadCompressionStats(),
            loadTopUsers(),
            loadHourlyDistribution()
        ]);
    }

    async function loadOverviewStats() {
        try {
            const response = await fetch('/admin/api/stats');
            const data = await response.json();

            const totalRecordsStat = document.getElementById('totalRecordsStat');
            const totalUsersStat = document.getElementById('totalUsersStat');
            const avgRecordsStat = document.getElementById('avgRecordsStat');
            
            if (totalRecordsStat) totalRecordsStat.textContent = data.totalRecords;
            if (totalUsersStat) totalUsersStat.textContent = data.totalUsers;

            const avgRecords = data.totalUsers > 0 ? Math.round(data.totalRecords / data.totalUsers) : 0;
            if (avgRecordsStat) avgRecordsStat.textContent = avgRecords;
        } catch (error) {
            console.error('Error loading stats:', error);
        }
    }

    async function loadUploadTrend() {
        try {
            const response = await fetch('/admin/api/stats/activity?days=' + currentDays);
            const data = await response.json();

            if (uploadTrendChart) {
                uploadTrendChart.data.labels = data.labels;
                uploadTrendChart.data.datasets[0].data = data.values;
                uploadTrendChart.update();
            }
        } catch (error) {
            console.error('Error loading upload trend:', error);
        }
    }

    async function loadUserActivity() {
        try {
            const response = await fetch('/admin/api/stats/user-activity?days=' + currentDays);
            const data = await response.json();

            if (userActivityChart) {
                userActivityChart.data.labels = data.labels;
                userActivityChart.data.datasets[0].data = data.values;
                userActivityChart.update();
            }
        } catch (error) {
            console.error('Error loading user activity:', error);
        }
    }

    async function loadStorageStats() {
        try {
            const response = await fetch('/admin/api/stats/storage');
            const data = await response.json();

            if (storageChart) {
                storageChart.data.datasets[0].data = [data.uncompressedSize, data.compressedSize];
                storageChart.update();
            }

            // 总数据量 = 未压缩数据 + 已压缩数据（使用压缩后的大小）
            const totalSize = data.uncompressedSize + data.compressedSize;
            const totalSizeStat = document.getElementById('totalSizeStat');
            if (totalSizeStat) totalSizeStat.textContent = formatBytes(totalSize);
        } catch (error) {
            console.error('Error loading storage stats:', error);
        }
    }

    async function loadCompressionStats() {
        try {
            const response = await fetch('/admin/api/stats/compression');
            const data = await response.json();

            if (compressionChart) {
                compressionChart.data.datasets[0].data = [
                    data.lessThan50,
                    data.range50to60,
                    data.range60to70,
                    data.range70to80,
                    data.greaterThan80
                ];
                compressionChart.update();
            }
        } catch (error) {
            console.error('Error loading compression stats:', error);
        }
    }

    async function loadTopUsers() {
        try {
            const response = await fetch('/admin/api/stats/top-users?limit=10');
            const data = await response.json();

            const tbody = document.getElementById('topUsersTable');
            if (!tbody) return;
            
            tbody.innerHTML = '';

            data.forEach(function(user, index) {
                const row = document.createElement('tr');
                row.innerHTML = '<td><span class="badge ' + (index < 3 ? 'badge-primary' : 'badge-info') + '">' + (index + 1) + '</span></td>' +
                    '<td>' + user.username + '</td>' +
                    '<td>' + user.recordCount + '</td>' +
                    '<td>' + user.dataPoints + '</td>' +
                    '<td>' + formatBytes(user.totalSize) + '</td>' +
                    '<td><a href="/admin/users/' + user.userId + '" class="btn btn-primary btn-sm">详情</a></td>';
                tbody.appendChild(row);
            });
        } catch (error) {
            console.error('Error loading top users:', error);
        }
    }

    async function loadHourlyDistribution() {
        try {
            const response = await fetch('/admin/api/stats/hourly');
            const data = await response.json();

            if (hourlyChart) {
                hourlyChart.data.datasets[0].data = data.values;
                hourlyChart.update();
            }
        } catch (error) {
            console.error('Error loading hourly distribution:', error);
        }
    }

    // Time range change handler
    const timeRange = document.getElementById('timeRange');
    if (timeRange) {
        timeRange.addEventListener('change', function() {
            currentDays = parseInt(this.value);
            loadAllData();
        });
    }

    // Initialize
    initCharts();
    loadAllData();
})();
