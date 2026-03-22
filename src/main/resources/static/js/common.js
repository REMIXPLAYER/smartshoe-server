/**
 * SmartShoe Admin - Common JavaScript
 * 所有管理后台页面共享的公共功能
 */

// Navigation sliding indicator with sessionStorage
function updateNavIndicator() {
    const nav = document.querySelector('.admin-nav');
    if (!nav) return;
    
    const activeLink = nav.querySelector('a.active');
    if (activeLink) {
        const navRect = nav.getBoundingClientRect();
        const linkRect = activeLink.getBoundingClientRect();
        const left = linkRect.left - navRect.left;
        const width = linkRect.width;
        
        // Check if we have a previous position stored
        const prevLeft = sessionStorage.getItem('navIndicatorLeft');
        const prevWidth = sessionStorage.getItem('navIndicatorWidth');
        
        if (prevLeft !== null && prevWidth !== null) {
            // Set initial position without animation
            nav.classList.add('initializing');
            nav.style.setProperty('--indicator-left', prevLeft + 'px');
            nav.style.setProperty('--indicator-width', prevWidth + 'px');
            
            // Force reflow
            nav.offsetHeight;
            
            // Remove initializing class and animate to new position
            nav.classList.remove('initializing');
        }
        
        // Animate to current position
        nav.style.setProperty('--indicator-left', left + 'px');
        nav.style.setProperty('--indicator-width', width + 'px');
        
        // Store current position for next page
        sessionStorage.setItem('navIndicatorLeft', left);
        sessionStorage.setItem('navIndicatorWidth', width);
    }
}

// Update on load and resize
window.addEventListener('load', updateNavIndicator);
window.addEventListener('resize', function() {
    const nav = document.querySelector('.admin-nav');
    if (nav) {
        nav.classList.add('initializing');
        updateNavIndicator();
        nav.classList.remove('initializing');
    }
});

// Utility function: Format bytes to human readable
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Utility function: Download file
function downloadFile(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
