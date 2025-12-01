import React from 'react';

export default function SkeletonList({ items = 5 }) {
  return (
    <div className="space-y-3 animate-pulse">
      {Array.from({ length: items }).map((_, index) => (
        <div 
          key={index} 
          className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg"
        >
          <div className="flex items-center gap-3 flex-1">
            <div className="w-10 h-10 bg-gray-200 dark:bg-gray-600 rounded-lg"></div>
            <div className="flex-1">
              <div className="h-4 bg-gray-200 dark:bg-gray-600 rounded w-3/4 mb-2"></div>
              <div className="h-3 bg-gray-200 dark:bg-gray-600 rounded w-1/2"></div>
            </div>
          </div>
          <div className="h-5 bg-gray-200 dark:bg-gray-600 rounded w-20"></div>
        </div>
      ))}
    </div>
  );
}

