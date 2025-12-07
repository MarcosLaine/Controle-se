import sharp from 'sharp';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { existsSync, mkdirSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const publicDir = join(__dirname, '../public');
const faviconPath = join(publicDir, 'favicon.png');

// Tamanhos dos ícones PWA
const iconSizes = [
  { size: 192, name: 'pwa-192x192.png' },
  { size: 512, name: 'pwa-512x512.png' }
];

async function generateIcons() {
  try {
    // Verificar se o favicon existe
    if (!existsSync(faviconPath)) {
      console.error('❌ favicon.png não encontrado em', publicDir);
      process.exit(1);
    }

    console.log('🔄 Gerando ícones PWA...');

    // Gerar cada tamanho de ícone
    for (const { size, name } of iconSizes) {
      const outputPath = join(publicDir, name);
      
      await sharp(faviconPath)
        .resize(size, size, {
          fit: 'contain',
          background: { r: 16, g: 185, b: 129, alpha: 1 } // #10b981 (verde do tema)
        })
        .png()
        .toFile(outputPath);

      console.log(`✅ Gerado: ${name} (${size}x${size})`);
    }

    console.log('✨ Ícones PWA gerados com sucesso!');
  } catch (error) {
    console.error('❌ Erro ao gerar ícones:', error.message);
    process.exit(1);
  }
}

generateIcons();


