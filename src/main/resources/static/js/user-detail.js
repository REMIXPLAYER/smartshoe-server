/**
 * SmartShoe Admin - User Detail Page JavaScript
 */

/* global Chart */

(function() {
    var userId = window.userId || '';

    window.toggleStatus = function(btn) {
        var id = btn.getAttribute('data-id');
        var newStatus = btn.textContent.includes('禁用') ? 'INACTIVE' : 'ACTIVE';
        
        fetch('/admin/api/users/' + id + '/status?status=' + newStatus, {method: 'POST'})
        .then(function(r) { return r.json(); })
        .then(function(data) {
            alert(data.message);
            if (data.success) location.reload();
        });
    };

    window.resetPassword = function(btn) {
        var id = btn.getAttribute('data-id');
        var newPassword = prompt('请输入新密码：');
        if (!newPassword) return;
        
        fetch('/admin/api/users/' + id + '/reset-password?newPassword=' + encodeURIComponent(newPassword), {method: 'POST'})
        .then(function(r) { return r.json(); })
        .then(function(data) {
            alert(data.message);
        });
    };

    window.delUser = function(btn) {
        var id = btn.getAttribute('data-id');
        var username = btn.getAttribute('data-username');
        
        if (!confirm('确定要删除用户 "' + username + '" 吗？此操作将同时删除该用户的所有数据记录！')) return;
        
        fetch('/admin/api/users/' + id + '/delete', {method: 'POST'})
        .then(function(r) { return r.json(); })
        .then(function(data) {
            alert(data.message);
            if (data.success) window.location.href = '/admin/users';
        });
    };

    window.delRecord = function(btn) {
        var recordId = btn.getAttribute('data-id');
        if (!confirm('确定要删除这条数据记录吗？')) return;
        
        fetch('/admin/api/records/' + recordId + '/delete', {method: 'POST'})
        .then(function(r) { return r.json(); })
        .then(function(data) {
            alert(data.message);
            if (data.success) location.reload();
        });
    };

    // Upload Chart
    var uploadCtx = document.getElementById('uploadChart');
    if (uploadCtx && userId) {
        fetch('/admin/api/users/' + userId + '/upload-stats')
            .then(function(r) { return r.json(); })
            .then(function(data) {
                new Chart(uploadCtx.getContext('2d'), {
                    type: 'bar',
                    data: {
                        labels: data.labels,
                        datasets: [{
                            label: '上传记录数',
                            data: data.values,
                            backgroundColor: '#7AAACE',
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
            });
    }
})();
