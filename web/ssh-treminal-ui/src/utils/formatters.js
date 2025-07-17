/**
 * 格式化文件大小
 * @param {number} size - 文件大小 (bytes)
 * @returns {string} 格式化后的字符串
 */
export function formatFileSize(size) {
    if (size == null) return '';
    if (size > 1024 * 1024 * 1024) return (size / (1024 ** 3)).toFixed(2) + ' GB';
    if (size > 1024 * 1024) return (size / (1024 ** 2)).toFixed(2) + ' MB';
    if (size > 1024) return (size / 1024).toFixed(1) + ' KB';
    return size + ' B';
}

/**
 * 格式化Unix时间戳为日期时间字符串
 * @param {number} mtime - Unix时间戳 (秒)
 * @returns {string} YYYY-MM-DD HH:mm
 */
export function formatMtime(mtime) {
    if (!mtime) return '';
    const d = new Date(mtime * 1000);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/**
 * 格式化速度
 * @param {number} bytesPerSec - 每秒字节数
 * @returns {string} 格式化后的速度字符串
 */
export function formatSpeed(bytesPerSec) {
    if (!bytesPerSec || bytesPerSec <= 0) return '';
    if (bytesPerSec > 1024 * 1024) return (bytesPerSec / 1024 / 1024).toFixed(2) + ' MB/s';
    if (bytesPerSec > 1024) return (bytesPerSec / 1024).toFixed(1) + ' KB/s';
    return bytesPerSec.toFixed(0) + ' B/s';
}
