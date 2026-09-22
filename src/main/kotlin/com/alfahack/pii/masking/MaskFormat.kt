package com.alfahack.pii.masking

/**
 * Формат маски ПД.
 *
 * - STAR: замена символов на `*` (токенизация с сохранением длины).
 * - TOKEN: замена на символ `X` (альтернативный формат).
 * - SYNTHETIC: генерация синтетических данных (заглушка, требует ML/словари).
 */
enum class MaskFormat {
    STAR,
    TOKEN,
    SYNTHETIC
}