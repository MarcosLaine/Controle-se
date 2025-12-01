import React from 'react';

export default function SkeletonTable({ rows = 5, columns = 4 }) {
  return (
    <div className="card">
      <div className="animate-pulse">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div className="h-6 bg-gray-200 dark:bg-gray-700 rounded w-32"></div>
          <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded w-24"></div>
        </div>
        
        {/* Table Header */}
        <div className="grid gap-4 mb-4" style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}>
          {Array.from({ length: columns }).map((_, i) => (
            <div key={i} className="h-4 bg-gray-200 dark:bg-gray-700 rounded"></div>
          ))}
        </div>
        
        {/* Table Rows */}
        <div className="space-y-3">
          {Array.from({ length: rows }).map((_, rowIndex) => (
            <div 
              key={rowIndex} 
              className="grid gap-4 py-3 border-b border-gray-200 dark:border-gray-700"
              style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
            >
              {Array.from({ length: columns }).map((_, colIndex) => (
                <div 
                  key={colIndex} 
                  className="h-4 bg-gray-200 dark:bg-gray-700 rounded"
                  style={{ width: colIndex === 0 ? '80%' : colIndex === columns - 1 ? '60%' : '100%' }}
                ></div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

