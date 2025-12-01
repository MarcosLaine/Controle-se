import React from 'react';

export default function SkeletonChart({ height = 'h-64', type = 'doughnut' }) {
  return (
    <div className="card">
      <div className="animate-pulse">
        <div className="h-6 bg-gray-200 dark:bg-gray-700 rounded w-48 mb-4"></div>
        <div className={`${height} bg-gray-200 dark:bg-gray-700 rounded-lg flex items-center justify-center`}>
          {type === 'doughnut' ? (
            <div className="w-32 h-32 bg-gray-300 dark:bg-gray-600 rounded-full"></div>
          ) : (
            <div className="w-full h-full bg-gray-300 dark:bg-gray-600 rounded"></div>
          )}
        </div>
      </div>
    </div>
  );
}

