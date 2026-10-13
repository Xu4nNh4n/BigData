use('supermarket_catalog');

db.products_catalog.createIndex({ sku: 1 }, { unique: true });
db.products_catalog.createIndex({ categoryName: 1, active: 1 });

db.products_catalog.insertMany([
  {
    sku: '1',
    name: 'Nước suối 500ml',
    price: NumberDecimal('5000'),
    categoryName: 'Đồ uống',
    supplierName: 'Fresh Farm',
    active: true,
    attributes: { volumeMl: 500, brand: 'Seed Water' },
    batches: [
      { batchCode: '1', quantity: 100, expiryDate: ISODate('2027-09-01') },
      { batchCode: '2', quantity: 40, expiryDate: ISODate('2026-12-01') }
    ]
  },
  {
    sku: '2',
    name: 'Sữa tươi không đường 1L',
    price: NumberDecimal('32000'),
    categoryName: 'Sữa và chế phẩm',
    supplierName: 'Fresh Farm',
    active: true,
    attributes: { volumeMl: 1000, sugarFree: true },
    batches: [
      { batchCode: '3', quantity: 60, expiryDate: ISODate('2026-11-20') }
    ]
  },
  {
    sku: '3',
    name: 'Mì gói vị bò hầm',
    price: NumberDecimal('6500'),
    categoryName: 'Thực phẩm khô',
    supplierName: 'Daily Food',
    active: true,
    attributes: { packageType: 'bag', flavor: 'beef' },
    batches: [
      { batchCode: '4', quantity: 120, expiryDate: ISODate('2027-09-15') }
    ]
  },
  {
    sku: '4',
    name: 'Nồi inox 18cm',
    price: NumberDecimal('185000'),
    categoryName: 'Đồ gia dụng',
    supplierName: 'Home Goods',
    active: true,
    attributes: { material: 'stainless_steel', diameterCm: 18 },
    batches: [
      { batchCode: '5', quantity: 25, expiryDate: null }
    ]
  },
  {
    sku: '5',
    name: 'Táo đỏ nhập khẩu',
    price: NumberDecimal('45000'),
    categoryName: 'Rau củ quả',
    supplierName: 'Fresh Farm',
    active: true,
    attributes: { origin: 'imported', unit: 'kg' },
    batches: [
      { batchCode: '6', quantity: 50, expiryDate: ISODate('2026-11-05') }
    ]
  }
]);

db.products_catalog.find({}, { _id: 0, sku: 1, name: 1, price: 1, categoryName: 1 });
