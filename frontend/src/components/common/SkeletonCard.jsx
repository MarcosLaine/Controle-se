import React from 'react';

export default function SkeletonCard({ className = '' }) {
  return (
    <div className={`card ${className}`}>
      <div className="animate-pulse">
        <div className="flex items-start justify-between mb-4">
          <div className="flex-1">
            <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-24 mb-2"></div>
            <div className="h-8 bg-gray-200 dark:bg-gray-700 rounded w-32"></div>
          </div>
          <div className="w-12 h-12 bg-gray-200 dark:bg-gray-700 rounded-xl"></div>
        </div>
      </div>
    </div>
  );
}

