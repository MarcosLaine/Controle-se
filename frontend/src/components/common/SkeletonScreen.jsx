import React from 'react';

export default function SkeletonScreen() {
  return (
    <div className="fixed inset-0 bg-white dark:bg-gray-900 z-50">
      <div className="flex flex-col items-center justify-center h-full">
        {/* Logo Skeleton */}
        <div className="mb-8 animate-pulse">
          <div className="w-16 h-16 bg-gray-200 dark:bg-gray-700 rounded-2xl mb-4"></div>
          <div className="h-8 bg-gray-200 dark:bg-gray-700 rounded w-48 mb-2"></div>
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-64"></div>
        </div>

        {/* Main Content Skeleton */}
        <div className="w-full max-w-md px-4 space-y-6">
          {/* Card Skeleton */}
          <div className="card animate-pulse">
            <div className="space-y-4">
              {/* Tabs */}
              <div className="flex gap-2">
                <div className="flex-1 h-10 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
                <div className="flex-1 h-10 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
              </div>

              {/* Form Fields */}
              <div className="space-y-4">
                <div>
                  <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-16 mb-2"></div>
                  <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded"></div>
                </div>
                <div>
                  <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-16 mb-2"></div>
                  <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded"></div>
                </div>
                <div className="h-12 bg-gray-200 dark:bg-gray-700 rounded"></div>
              </div>
            </div>
          </div>

          {/* Dashboard Preview Skeleton */}
          <div className="space-y-4 animate-pulse">
            <div className="h-6 bg-gray-200 dark:bg-gray-700 rounded w-32"></div>
            <div className="grid grid-cols-2 gap-4">
              <div className="h-24 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
              <div className="h-24 bg-gray-200 dark:bg-gray-700 rounded-lg"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

