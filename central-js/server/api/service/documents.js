var express = require('express');
var router = express.Router();
var documents = require('./documents.controller');

router.get('/', documents.index);
router.get('/:nID', documents.getDocument);
router.get('/download/:nID', documents.getDocumentFile);

module.exports = router;