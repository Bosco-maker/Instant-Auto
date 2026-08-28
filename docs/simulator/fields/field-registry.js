/**
 * Field Registry - Maps field IDs to image paths and dimensions
 * Each field is 144" x 144" (standard FTC field size)
 */

export const FIELD_IMAGES = {
  'decode-2025': {
    id: 'decode-2025',
    name: 'DECODE',
    season: '2025-26',
    image: 'assets/fields/DECODE.svg',
    width: 144,
    height: 144,
  },
  'into-the-deep-2024': {
    id: 'into-the-deep-2024',
    name: 'INTO THE DEEP',
    season: '2024-25',
    image: 'assets/fields/INTO_THE_DEEP.svg',
    width: 144,
    height: 144,
  },
  'standard-ftc': {
    id: 'standard-ftc',
    name: 'Standard FTC Field',
    season: 'Generic',
    image: 'assets/fields/standard-ftc.svg',
    width: 144,
    height: 144,
  }
};

export function getFieldConfig(fieldId) {
  return FIELD_IMAGES[fieldId] || FIELD_IMAGES['decode-2025'];
}

export function getAllFieldIds() {
  return Object.keys(FIELD_IMAGES);
}

export function getFieldOptions() {
  return Object.values(FIELD_IMAGES).map(f => ({ id: f.id, name: f.name, season: f.season }));
}