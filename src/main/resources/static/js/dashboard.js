/**
 * SmartShoe Admin - Dashboard Page JavaScript
 */

/* global Chart */

(function() {
    // User Status Chart
    const statusCtx = document.getElementById('statusChart');
    if (statusCtx) {
        new Chart(statusCtx.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: ['活跃用户', '非活跃用户'],
                datasets: [{
                    data: [window.activeUsers || 0, window.inactiveUsers || 0],
                    backgroundColor: ['#7AAACE', '#9CD5FF'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom'
                    }
                }
            }
        });
    }

    // Activity Chart
    const activityCtx = document.getElementById('activityChart');
    let activityChart = null;
    
    if (activityCtx) {
        activityChart = new Chart(activityCtx.getContext('2d'), {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: '数据记录数',
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
                plugins: {
                    legend: {
                        display: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            stepSize: 1
                        }
                    }
                }
            }
        });
    }

    // Load activity data
    function loadActivityData(days) {
        fetch(`/admin/api/stats/activity?days=${days}`)
            .then(r => r.json())
            .then(data => {
                if (activityChart) {
                    activityChart.data.labels = data.labels;
                    activityChart.data.datasets[0].data = data.values;
                    activityChart.update();
                }
            })
            .catch(err => console.log('Failed to load activity data'));
    }

    // Initial load
    loadActivityData(7);

    // Period change handler
    const chartPeriod = document.getElementById('chartPeriod');
    if (chartPeriod) {
        chartPeriod.addEventListener('change', function() {
            loadActivityData(this.value);
        });
    }
})();
