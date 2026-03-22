/**
 * SmartShoe Admin - Records Page JavaScript
 */

(function() {
    // Store all records for filtering and sorting
    var allRecords = [];
    var currentSort = { column: null, direction: 'asc' };
    
    // Initialize
    document.addEventListener('DOMContentLoaded', function() {
        var rows = document.querySelectorAll('.record-row');
        rows.forEach(function(row) {
            allRecords.push({
                element: row,
                username: row.getAttribute('data-username').toLowerCase(),
                date: row.getAttribute('data-date'),
                size: parseInt(row.getAttribute('data-size')) || 0,
                ratio: parseFloat(row.getAttribute('data-ratio')) || 0,
                dataCount: parseInt(row.getAttribute('data-datacount')) || 0,
                interval: parseInt(row.getAttribute('data-interval')) || 0,
                compressedSize: parseInt(row.getAttribute('data-compressedsize')) || 0,
                visible: true
            });
        });
    });
    
    window.applyFilters = function() {
        var usernameFilter = document.getElementById('filterUsername').value.toLowerCase();
        var startDate = document.getElementById('filterStartDate').value;
        var endDate = document.getElementById('filterEndDate').value;
        var minSize = parseFloat(document.getElementById('filterMinSize').value) || 0;
        var maxSize = parseFloat(document.getElementById('filterMaxSize').value) || Infinity;
        var minRatio = parseFloat(document.getElementById('filterMinRatio').value) || 0;
        var maxRatio = parseFloat(document.getElementById('filterMaxRatio').value) || 100;
        
        var visibleCount = 0;
        
        allRecords.forEach(function(record) {
            var show = true;
            
            // Username filter
            if (usernameFilter && !record.username.includes(usernameFilter)) {
                show = false;
            }
            
            // Date filter
            if (startDate || endDate) {
                var recordDate = record.date.split(' ')[0];
                if (startDate && recordDate < startDate) show = false;
                if (endDate && recordDate > endDate) show = false;
            }
            
            // Size filter (convert bytes to KB)
            var sizeKB = record.size / 1024;
            if (sizeKB < minSize || sizeKB > maxSize) {
                show = false;
            }
            
            // Ratio filter
            if (record.ratio < minRatio || record.ratio > maxRatio) {
                show = false;
            }
            
            record.element.style.display = show ? '' : 'none';
            record.visible = show;
            if (show) visibleCount++;
        });
        
        document.getElementById('filteredCount').textContent = visibleCount + ' 条记录';
        updateEmptyState(visibleCount);
    };
    
    window.resetFilters = function() {
        document.getElementById('filterUsername').value = '';
        document.getElementById('filterStartDate').value = '';
        document.getElementById('filterEndDate').value = '';
        document.getElementById('filterMinSize').value = '';
        document.getElementById('filterMaxSize').value = '';
        document.getElementById('filterMinRatio').value = '';
        document.getElementById('filterMaxRatio').value = '';
        
        allRecords.forEach(function(record) {
            record.element.style.display = '';
            record.visible = true;
        });
        
        document.getElementById('filteredCount').textContent = allRecords.length + ' 条记录';
        updateEmptyState(allRecords.length);
        
        // Reset sort indicators
        document.querySelectorAll('.sortable').forEach(function(th) {
            th.classList.remove('sort-asc', 'sort-desc');
        });
        currentSort = { column: null, direction: 'asc' };
    };
    
    function updateEmptyState(count) {
        var tbody = document.getElementById('recordsTableBody');
        var existingFilterEmpty = document.getElementById('filterEmptyState');
        
        if (existingFilterEmpty) {
            existingFilterEmpty.remove();
        }
        
        if (count === 0) {
            var filterEmpty = document.createElement('div');
            filterEmpty.id = 'filterEmptyState';
            filterEmpty.className = 'empty-state';
            filterEmpty.innerHTML = '<svg class="icon icon-xl" style="opacity: 0.5;"><use href="/icons/icons.svg#icon-folder"/></svg><p>没有符合筛选条件的记录</p>';
            tbody.parentElement.parentElement.appendChild(filterEmpty);
            tbody.parentElement.style.display = 'none';
        } else {
            tbody.parentElement.style.display = 'block';
        }
    }
    
    window.sortTable = function(column) {
        var tbody = document.getElementById('recordsTableBody');
        var rows = Array.from(tbody.querySelectorAll('.record-row'));
        
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
                case 'dataCount':
                    aVal = parseInt(a.getAttribute('data-datacount')) || 0;
                    bVal = parseInt(b.getAttribute('data-datacount')) || 0;
                    break;
                case 'interval':
                    aVal = parseInt(a.getAttribute('data-interval')) || 0;
                    bVal = parseInt(b.getAttribute('data-interval')) || 0;
                    break;
                case 'originalSize':
                    aVal = parseInt(a.getAttribute('data-size')) || 0;
                    bVal = parseInt(b.getAttribute('data-size')) || 0;
                    break;
                case 'compressedSize':
                    aVal = parseInt(a.getAttribute('data-compressedsize')) || 0;
                    bVal = parseInt(b.getAttribute('data-compressedsize')) || 0;
                    break;
                case 'compressionRatio':
                    aVal = parseFloat(a.getAttribute('data-ratio')) || 0;
                    bVal = parseFloat(b.getAttribute('data-ratio')) || 0;
                    break;
                case 'createdAt':
                    aVal = new Date(a.getAttribute('data-date')).getTime();
                    bVal = new Date(b.getAttribute('data-date')).getTime();
                    break;
                default:
                    return 0;
            }
            
            if (currentSort.direction === 'asc') {
                return aVal - bVal;
            } else {
                return bVal - aVal;
            }
        });
        
        // Re-append sorted rows
        rows.forEach(function(row) {
            tbody.appendChild(row);
        });
    };

    window.delRecord = function(btn) {
        const recordId = btn.getAttribute('data-id');
        if (!confirm('确定要删除这条数据记录吗？')) return;
        
        fetch('/admin/api/records/' + recordId + '/delete', {method: 'POST'})
        .then(r => r.json())
        .then(data => {
            alert(data.message);
            if (data.success) location.reload();
        });
    };
})();
