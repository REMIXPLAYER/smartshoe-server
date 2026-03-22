/**
 * SmartShoe Admin - Users Page JavaScript
 */

(function() {
    // Store all users for filtering
    var allUsers = [];
    var currentSort = { column: null, direction: 'asc' };

    // Initialize
    document.addEventListener('DOMContentLoaded', function() {
        var rows = document.querySelectorAll('.user-row');
        rows.forEach(function(row) {
            allUsers.push({
                element: row,
                userId: row.getAttribute('data-userid'),
                username: row.getAttribute('data-username').toLowerCase(),
                email: row.getAttribute('data-email').toLowerCase(),
                status: row.getAttribute('data-status'),
                createdAt: row.getAttribute('data-created'),
                lastLoginAt: row.getAttribute('data-lastlogin')
            });
        });
    });

    // Sort table function
    window.sortTable = function(column) {
        var tbody = document.getElementById('usersTableBody');
        var rows = Array.from(tbody.querySelectorAll('.user-row'));

        // Toggle direction if same column
        if (currentSort.column === column) {
            currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
        } else {
            currentSort.column = column;
            currentSort.direction = 'asc';
        }

        // Update sort indicators
        document.querySelectorAll('.sortable').forEach(function(th) {
            th.classList.remove('sort-asc', 'sort-desc');
        });
        var currentTh = document.querySelector('.sortable[data-column="' + column + '"]');
        if (currentTh) {
            currentTh.classList.add(currentSort.direction === 'asc' ? 'sort-asc' : 'sort-desc');
        }

        // Sort rows
        rows.sort(function(a, b) {
            var aVal, bVal;

            switch(column) {
                case 'status':
                    aVal = a.getAttribute('data-status') || '';
                    bVal = b.getAttribute('data-status') || '';
                    // ACTIVE comes before INACTIVE
                    if (aVal === bVal) return 0;
                    if (currentSort.direction === 'asc') {
                        return aVal === 'ACTIVE' ? -1 : 1;
                    } else {
                        return aVal === 'ACTIVE' ? 1 : -1;
                    }
                case 'createdAt':
                    aVal = new Date(a.getAttribute('data-created')).getTime() || 0;
                    bVal = new Date(b.getAttribute('data-created')).getTime() || 0;
                    break;
                case 'lastLogin':
                    var aLogin = a.getAttribute('data-lastlogin');
                    var bLogin = b.getAttribute('data-lastlogin');
                    // Handle "从未登录" case
                    if (aLogin === '从未登录' || !aLogin) aVal = 0;
                    else aVal = new Date(aLogin).getTime();
                    if (bLogin === '从未登录' || !bLogin) bVal = 0;
                    else bVal = new Date(bLogin).getTime();
                    break;
                default:
                    return 0;
            }

            if (column !== 'status') {
                if (currentSort.direction === 'asc') {
                    return aVal - bVal;
                } else {
                    return bVal - aVal;
                }
            }
            return 0;
        });

        // Re-append sorted rows
        rows.forEach(function(row) {
            tbody.appendChild(row);
        });
    };

    window.applyFilters = function() {
        var searchTerm = document.getElementById('searchInput').value.toLowerCase();
        var statusFilter = document.getElementById('statusFilter').value;
        var regStart = document.getElementById('regStartDate').value;
        var regEnd = document.getElementById('regEndDate').value;
        var loginStart = document.getElementById('loginStartDate').value;
        var loginEnd = document.getElementById('loginEndDate').value;

        var visibleCount = 0;

        allUsers.forEach(function(user) {
            var show = true;

            // Search filter (username or email)
            if (searchTerm && !user.username.includes(searchTerm) && !user.email.includes(searchTerm)) {
                show = false;
            }

            // Status filter
            if (statusFilter && user.status !== statusFilter) {
                show = false;
            }

            // Registration date filter
            if (regStart || regEnd) {
                var regDate = user.createdAt ? user.createdAt.split(' ')[0] : '';
                if (regStart && regDate < regStart) show = false;
                if (regEnd && regDate > regEnd) show = false;
            }

            // Last login date filter
            if (loginStart || loginEnd) {
                var loginDate = user.lastLoginAt && user.lastLoginAt !== '从未登录'
                    ? user.lastLoginAt.split(' ')[0]
                    : '';
                if (loginStart && (!loginDate || loginDate < loginStart)) show = false;
                if (loginEnd && (!loginDate || loginDate > loginEnd)) show = false;
            }

            user.element.style.display = show ? '' : 'none';
            if (show) visibleCount++;
        });

        // Update filtered count
        document.getElementById('filteredCount').textContent = visibleCount + ' 位用户';
        updateEmptyState(visibleCount);
    };

    window.resetFilters = function() {
        document.getElementById('searchInput').value = '';
        document.getElementById('statusFilter').value = '';
        document.getElementById('regStartDate').value = '';
        document.getElementById('regEndDate').value = '';
        document.getElementById('loginStartDate').value = '';
        document.getElementById('loginEndDate').value = '';

        allUsers.forEach(function(user) {
            user.element.style.display = '';
        });

        document.getElementById('filteredCount').textContent = allUsers.length + ' 位用户';
        updateEmptyState(allUsers.length);

        // Reset sort indicators
        document.querySelectorAll('.sortable').forEach(function(th) {
            th.classList.remove('sort-asc', 'sort-desc');
        });
        currentSort = { column: null, direction: 'asc' };
    };

    function updateEmptyState(count) {
        var tbody = document.getElementById('usersTableBody');
        var existingFilterEmpty = document.getElementById('filterEmptyState');

        if (existingFilterEmpty) {
            existingFilterEmpty.remove();
        }

        if (count === 0) {
            var filterEmpty = document.createElement('div');
            filterEmpty.id = 'filterEmptyState';
            filterEmpty.className = 'empty-state';
            filterEmpty.innerHTML = '<svg class="icon icon-xl" style="opacity: 0.5;"><use href="/icons/icons.svg#icon-users"/></svg><p>没有符合筛选条件的用户</p>';
            tbody.parentElement.parentElement.appendChild(filterEmpty);
            tbody.parentElement.style.display = 'none';
        } else {
            tbody.parentElement.style.display = 'block';
        }
    }

    // Edit user
    window.editUser = function(btn) {
        const userId = btn.getAttribute('data-id');

        fetch('/admin/api/users/' + userId)
            .then(r => r.json())
            .then(user => {
                document.getElementById('editUserId').value = user.userId;
                document.getElementById('editUsername').value = user.username;
                document.getElementById('editEmail').value = user.email;
                document.getElementById('editPassword').value = '';
                document.getElementById('editModal').style.display = 'flex';
            });
    };

    window.closeEditModal = function() {
        document.getElementById('editModal').style.display = 'none';
    };

    // Edit form submission
    const editForm = document.getElementById('editForm');
    if (editForm) {
        editForm.addEventListener('submit', function(e) {
            e.preventDefault();
            const userId = document.getElementById('editUserId').value;
            const formData = new FormData();
            formData.append('username', document.getElementById('editUsername').value);
            formData.append('email', document.getElementById('editEmail').value);
            const password = document.getElementById('editPassword').value;
            if (password) formData.append('password', password);

            fetch('/admin/api/users/' + userId + '/update', {
                method: 'POST',
                body: formData
            })
            .then(r => r.json())
            .then(data => {
                alert(data.message);
                if (data.success) {
                    closeEditModal();
                    location.reload();
                }
            });
        });
    }

    // Delete user
    window.delUser = function(btn) {
        const userId = btn.getAttribute('data-id');
        const username = btn.getAttribute('data-username');

        if (!confirm('确定要删除用户 "' + username + '" 吗？此操作将同时删除该用户的所有数据记录！')) return;

        fetch('/admin/api/users/' + userId + '/delete', {method: 'POST'})
        .then(r => r.json())
        .then(data => {
            alert(data.message);
            if (data.success) location.reload();
        });
    };

    // Close modal on outside click
    const editModal = document.getElementById('editModal');
    if (editModal) {
        editModal.addEventListener('click', function(e) {
            if (e.target === this) closeEditModal();
        });
    }
})();
