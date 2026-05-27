-- Add NDK (unstripped .so obj zip) to symbol_files.framework enum
ALTER TABLE symbol_files
    MODIFY COLUMN framework ENUM('js', 'mapping', 'dsym', 'ndk') NOT NULL;
