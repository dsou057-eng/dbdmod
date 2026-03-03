package com.example.dbdcore.character;

/**
 * Маркерный интерфейс для перков.
 * Конкретные реализации и их эффекты подключаются отдельными модулями,
 * чтобы не зашивать баланс и способности в ядро.
 */
public interface Perk {
    String getId();
}

