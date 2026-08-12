const functions = require('@google-cloud/functions-framework');
const admin = require('firebase-admin');
const { Storage } = require('@google-cloud/storage');
const { AzureKeyCredential } = require('@azure/core-auth');
const {
  DocumentAnalysisClient
} = require('@azure/ai-form-recognizer');

admin.initializeApp();

const db = admin.firestore();
const storage = new Storage();

const azureClient = new DocumentAnalysisClient(
  process.env.AZURE_DI_ENDPOINT,
  new AzureKeyCredential(process.env.AZURE_DI_KEY)
);

/**
 * Removes repeated spaces and trims the text.
 */
function normalize(text) {
  return (text || '')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Creates simplified lists from Azure table cells and key-value pairs.
 */
function buildSources(azureExtraction) {
  const tableChunks = [];
  const keyValueChunks = [];

  for (const pair of azureExtraction.keyValuePairs || []) {
    keyValueChunks.push({
      key: normalize(pair.key),
      value: normalize(pair.value),
      text: normalize(`${pair.key || ''} ${pair.value || ''}`),
      confidence: pair.confidence || null
    });
  }

  for (const table of azureExtraction.tables || []) {
    for (const row of table.rows || []) {
      for (const cell of row.cells || []) {
        const text = normalize(cell);

        if (text) {
          tableChunks.push(text);
        }
      }
    }
  }

  return {
    tableChunks,
    keyValueChunks
  };
}

/**
 * Finds a table cell containing one of the supplied labels and extracts
 * a value from the same cell using the supplied regular expression.
 */
function findByLabelsInTables(tableChunks, labels, regex) {
  for (const text of tableChunks) {
    if (labels.some(label => text.includes(label))) {
      const match = text.match(regex);

      if (match) {
        return match[1] || match[0];
      }
    }
  }

  return null;
}

/**
 * Finds text that appears after a known label in the same table cell.
 */
function findTextAfterLabelInTables(tableChunks, labels) {
  for (const text of tableChunks) {
    for (const label of labels) {
      if (text.includes(label)) {
        const extractedValue = text
          .replace(label, '')
          .replace(/[:：]/g, '')
          .trim();

        return extractedValue || null;
      }
    }
  }

  return null;
}

/**
 * Finds a selected option from Azure key-value extraction.
 */
function findSelectedOption(keyValueChunks, options) {
  for (const option of options) {
    for (const item of keyValueChunks) {
      const key = item.key || '';
      const value = (item.value || '').toLowerCase();

      if (
        key.includes(option) &&
        value.includes('selected') &&
        !value.includes('unselected')
      ) {
        return option;
      }
    }
  }

  return null;
}

/**
 * Finds the value in the row directly below a matching table header.
 */
function findValueUnderHeaderRow(tables, labels) {
  for (const table of tables || []) {
    const rows = table.rows || [];

    for (let rowIndex = 0; rowIndex < rows.length - 1; rowIndex++) {
      const headerRow = rows[rowIndex].cells || [];
      const valueRow = rows[rowIndex + 1].cells || [];

      for (
        let columnIndex = 0;
        columnIndex < headerRow.length;
        columnIndex++
      ) {
        const header = normalize(headerRow[columnIndex]);

        if (labels.some(label => header.includes(label))) {
          const value = normalize(valueRow[columnIndex]);

          if (value) {
            return value;
          }
        }
      }
    }
  }

  return null;
}

/**
 * Finds a known Israeli health fund name.
 */
function findKnownHealthFund(tableChunks) {
  const funds = [
    'מכבי',
    'כללית',
    'מאוחדת',
    'לאומית'
  ];

  for (const text of tableChunks) {
    for (const fund of funds) {
      if (text.includes(fund)) {
        return fund;
      }
    }
  }

  return null;
}

/**
 * Validates and normalizes a house-number candidate.
 *
 * This prevents values such as an Israeli identity number or text
 * containing "תעודת זהות" from being stored as the house number.
 */
function normalizeHouseNumber(value) {
  const normalizedValue = normalize(value);

  if (!normalizedValue) {
    return null;
  }

  if (
    normalizedValue.includes('תעודת זהות') ||
    normalizedValue.includes('מספר זהות') ||
    normalizedValue.includes('מספר תעודת זהות')
  ) {
    return null;
  }

  const compactValue = normalizedValue.replace(/\s+/g, '');

  // Prevent a nine-digit Israeli identity number.
  if (/^\d{9}$/.test(compactValue)) {
    return null;
  }

  /*
   * Supported examples:
   * 12
   * 12א
   * 12/A
   * 12-א
   */
  const match = compactValue.match(
    /^\d{1,5}(?:[א-תA-Za-z/-]{0,4})?$/
  );

  return match ? match[0] : null;
}

/**
 * Builds the structured data that will later be used for automatic
 * form completion.
 */
function buildAutofillSuggestions(azureExtraction) {
  const {
    tableChunks,
    keyValueChunks
  } = buildSources(azureExtraction);

  /*
   * Important:
   * Do not use the general label "מספר" here.
   *
   * It also appears in labels such as:
   * "מספר תעודת זהות"
   * "מספר חשבון"
   * "מספר סניף"
   */
  const rawHouseNumber =
    findTextAfterLabelInTables(
      tableChunks,
      [
        'מספר בית',
        'מס׳ בית',
        "מס' בית",
        'מס בית'
      ]
    ) ||
    findValueUnderHeaderRow(
      azureExtraction.tables,
      [
        'מספר בית',
        'מס׳ בית',
        "מס' בית",
        'מס בית'
      ]
    );

  const houseNumber =
    normalizeHouseNumber(rawHouseNumber);

  console.log(
    'HOUSE_NUMBER_MAPPING',
    {
      rawHouseNumber,
      normalizedHouseNumber: houseNumber
    }
  );

  return {
    personalDetails: {
      idNumber: findByLabelsInTables(
        tableChunks,
        [
          'תעודת זהות',
          'מספר זהות',
          'מספר תעודת זהות'
        ],
        /\b\d{9}\b/
      ),

      firstName: findTextAfterLabelInTables(
        tableChunks,
        ['שם פרטי']
      ),

      lastName: findTextAfterLabelInTables(
        tableChunks,
        ['שם משפחה']
      ),

      fatherName: findTextAfterLabelInTables(
        tableChunks,
        ['שם האב']
      ),

      birthDate: findByLabelsInTables(
        tableChunks,
        ['תאריך לידה'],
        /\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b/
      ),

      birthCountry: findTextAfterLabelInTables(
        tableChunks,
        ['ארץ לידה']
      ),

      gender: findSelectedOption(
        keyValueChunks,
        ['זכר', 'נקבה']
      ),

      maritalStatus: findSelectedOption(
        keyValueChunks,
        [
          'רווק',
          'ידוע',
          'נשוי',
          'גרוש',
          'אלמן'
        ]
      )
    },

    contactDetails: {
      phone: findByLabelsInTables(
        tableChunks,
        ['טלפון', 'נייד'],
        /05\d[- ]?\d{7}/
      ),

      email: findByLabelsInTables(
        tableChunks,
        [
          'דואר אלקטרוני',
          'אימייל',
          'מייל'
        ],
        /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i
      )
    },

    address: {
      city: findTextAfterLabelInTables(
        tableChunks,
        [
          'ישוב',
          'יישוב',
          'עיר'
        ]
      ),

      street: findTextAfterLabelInTables(
        tableChunks,
        ['רחוב']
      ),

      houseNumber,

      apartment: findTextAfterLabelInTables(
        tableChunks,
        ['דירה']
      ),

      zipCode: findByLabelsInTables(
        tableChunks,
        ['מיקוד'],
        /\b\d{5,7}\b/
      )
    },

    healthFund: {
      name:
        findTextAfterLabelInTables(
          tableChunks,
          [
            'קופת חולים',
            'שם קופה',
            'שם הקופה'
          ]
        ) ||
        findValueUnderHeaderRow(
          azureExtraction.tables,
          [
            'קופת חולים',
            'שם קופה',
            'שם הקופה'
          ]
        ) ||
        findKnownHealthFund(tableChunks)
    },

    bankDetails: {
      bankName:
        findTextAfterLabelInTables(
          tableChunks,
          [
            'שם הבנק',
            'שם בנק'
          ]
        ) ||
        findValueUnderHeaderRow(
          azureExtraction.tables,
          [
            'שם הבנק',
            'שם בנק'
          ]
        ),

      branchNumber:
        findByLabelsInTables(
          tableChunks,
          [
            'מספר סניף',
            'סניף'
          ],
          /\b\d{2,4}\b/
        ) ||
        findValueUnderHeaderRow(
          azureExtraction.tables,
          [
            'מספר סניף',
            'סניף'
          ]
        ),

      accountNumber:
        findByLabelsInTables(
          tableChunks,
          [
            'מספר חשבון',
            'חשבון'
          ],
          /\b\d{4,12}\b/
        ) ||
        findValueUnderHeaderRow(
          azureExtraction.tables,
          [
            'מספר חשבון',
            'חשבון'
          ]
        )
    }
  };
}

functions.http(
  'processPdfAzure',
  async (req, res) => {
    try {
      const fileId = req.query.fileId;

      if (!fileId) {
        return res.status(400).json({
          error: 'Missing fileId'
        });
      }

      let userId = null;

      const authHeader =
        req.headers.authorization || '';

      if (authHeader.startsWith('Bearer ')) {
        const idToken =
          authHeader.replace('Bearer ', '');

        const decodedToken =
          await admin.auth().verifyIdToken(idToken);

        userId = decodedToken.uid;
      } else {
        userId = req.query.userId;
      }

      if (!userId) {
        return res.status(401).json({
          error: 'Missing authenticated user'
        });
      }

      const uploadedFileRef = db
        .collection('users')
        .doc(userId)
        .collection('uploadedFiles')
        .doc(fileId);

      const uploadedFileSnap =
        await uploadedFileRef.get();

      if (!uploadedFileSnap.exists) {
        return res.status(404).json({
          error: 'Uploaded file document not found'
        });
      }

      const uploadedFileData =
        uploadedFileSnap.data();

      const storagePath =
        uploadedFileData.storagePath;

      if (!storagePath) {
        return res.status(400).json({
          error: 'Missing storagePath'
        });
      }

      const bucketName =
        'easyfill-1db36.firebasestorage.app';

      const [fileBuffer] = await storage
        .bucket(bucketName)
        .file(storagePath)
        .download();

      const poller =
        await azureClient.beginAnalyzeDocument(
          'prebuilt-document',
          fileBuffer
        );

      const result =
        await poller.pollUntilDone();

      const keyValuePairs = [];

      for (const pair of result.keyValuePairs || []) {
        keyValuePairs.push({
          key: pair.key?.content || null,
          value: pair.value?.content || null,
          confidence: pair.confidence || null
        });
      }

      const tables = [];

      for (const table of result.tables || []) {
        const rows = [];

        for (
          let rowIndex = 0;
          rowIndex < table.rowCount;
          rowIndex++
        ) {
          const row = [];

          for (
            let columnIndex = 0;
            columnIndex < table.columnCount;
            columnIndex++
          ) {
            const cell = table.cells.find(
              currentCell =>
                currentCell.rowIndex === rowIndex &&
                currentCell.columnIndex === columnIndex
            );

            row.push(cell?.content || '');
          }

          rows.push({
            rowIndex,
            cells: row
          });
        }

        tables.push({
          rowCount: table.rowCount,
          columnCount: table.columnCount,
          rows
        });
      }

      const selectionMarks = [];

      for (const page of result.pages || []) {
        for (
          const mark of page.selectionMarks || []
        ) {
          selectionMarks.push({
            pageNumber: page.pageNumber,
            state: mark.state,
            confidence: mark.confidence || null
          });
        }
      }

      const azureExtraction = {
        keyValuePairs,
        tables,
        selectionMarks,
        pageCount: result.pages?.length || 0,
        createdAt:
          admin.firestore.FieldValue.serverTimestamp()
      };

      const autofillSuggestions =
        buildAutofillSuggestions(
          azureExtraction
        );

      console.log(
        'AUTOFILL_SUGGESTIONS',
        JSON.stringify(
          autofillSuggestions,
          null,
          2
        )
      );

      await uploadedFileRef
        .collection('azureExtractions')
        .doc('latest')
        .set(azureExtraction);

      await uploadedFileRef
        .collection('autofillSuggestions')
        .doc('latest')
        .set({
          suggestions: autofillSuggestions,
          createdAt:
            admin.firestore.FieldValue.serverTimestamp()
        });

      return res.json({
        message:
          'Azure extraction completed',
        userId,
        fileId,
        extraction: azureExtraction,
        autofillSuggestions
      });
    } catch (error) {
      console.error(
        'FULL ERROR:',
        error
      );

      return res.status(500).json({
        error:
          error?.message ||
          'Unknown server error'
      });
    }
  }
);