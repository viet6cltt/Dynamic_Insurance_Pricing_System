import React from "react";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

/**
 * Reusable pagination component.
 * Props:
 *  - currentPage: number (1-indexed)
 *  - totalPages: number
 *  - onPageChange: (page: number) => void
 *  - pageSize: number
 *  - onPageSizeChange: (size: number) => void
 *  - totalItems: number
 *  - pageSizeOptions: number[] (default [10, 20, 50])
 */
export default function Pagination({
  currentPage,
  totalPages,
  onPageChange,
  pageSize,
  onPageSizeChange,
  totalItems,
  pageSizeOptions = [10, 20, 50],
}) {
  if (totalItems === 0) return null;

  // Tạo danh sách số trang hiển thị (tối đa 5 nút)
  const getPageNumbers = () => {
    if (totalPages <= 5) return Array.from({ length: totalPages }, (_, i) => i + 1);
    if (currentPage <= 3) return [1, 2, 3, 4, 5];
    if (currentPage >= totalPages - 2) return [totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
    return [currentPage - 2, currentPage - 1, currentPage, currentPage + 1, currentPage + 2];
  };

  const pageNumbers = getPageNumbers();
  const startItem = (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalItems);

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-6 py-4 border-t border-gray-100">
      {/* Left: info + page size */}
      <div className="flex items-center space-x-3 text-sm text-gray-500">
        <span>
          Hiển thị{" "}
          <span className="font-bold text-gray-700">{startItem}–{endItem}</span>
          {" "}trong{" "}
          <span className="font-bold text-gray-700">{totalItems}</span>
          {" "}bản ghi
        </span>
        <span className="text-gray-300">|</span>
        <div className="flex items-center space-x-1.5">
          <span className="text-gray-400 text-xs">Hiện:</span>
          <select
            value={pageSize}
            onChange={(e) => {
              onPageSizeChange(Number(e.target.value));
              onPageChange(1);
            }}
            className="bg-gray-50 border border-gray-200 rounded-lg px-2 py-1 text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-blue-400"
          >
            {pageSizeOptions.map((s) => (
              <option key={s} value={s}>{s} / trang</option>
            ))}
          </select>
        </div>
      </div>

      {/* Right: page buttons */}
      <div className="flex items-center space-x-1">
        {/* First */}
        <button
          onClick={() => onPageChange(1)}
          disabled={currentPage === 1}
          className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          title="Trang đầu"
        >
          <ChevronsLeft className="w-4 h-4" />
        </button>

        {/* Prev */}
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 1}
          className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          title="Trang trước"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        {/* Page numbers */}
        {pageNumbers[0] > 1 && (
          <>
            <button
              onClick={() => onPageChange(1)}
              className="min-w-[32px] h-8 px-2 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-100 transition-colors"
            >1</button>
            {pageNumbers[0] > 2 && (
              <span className="px-1 text-gray-300 text-xs">…</span>
            )}
          </>
        )}

        {pageNumbers.map((page) => (
          <button
            key={page}
            onClick={() => onPageChange(page)}
            className={`min-w-[32px] h-8 px-2 rounded-lg text-xs font-bold transition-all ${
              page === currentPage
                ? "bg-blue-600 text-white shadow-sm"
                : "text-gray-500 hover:bg-gray-100"
            }`}
          >
            {page}
          </button>
        ))}

        {pageNumbers[pageNumbers.length - 1] < totalPages && (
          <>
            {pageNumbers[pageNumbers.length - 1] < totalPages - 1 && (
              <span className="px-1 text-gray-300 text-xs">…</span>
            )}
            <button
              onClick={() => onPageChange(totalPages)}
              className="min-w-[32px] h-8 px-2 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-100 transition-colors"
            >{totalPages}</button>
          </>
        )}

        {/* Next */}
        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
          className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          title="Trang sau"
        >
          <ChevronRight className="w-4 h-4" />
        </button>

        {/* Last */}
        <button
          onClick={() => onPageChange(totalPages)}
          disabled={currentPage === totalPages}
          className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          title="Trang cuối"
        >
          <ChevronsRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
