#!/usr/bin/env python3
"""Emit fixed failure codes/classes, never alias/password/key material or raw tool output."""
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text(errors='replace').lower()
phase=sys.argv[2] if len(sys.argv)>2 else 'key'
code='SIGNING_TOOL_FAILURE_CHECK_SECURE_ACTIONS_LOG'
# Check nested compatibility/integrity causes before generic password wrapper messages.
for needle,value in [
    ('unsupportedclassversion','SIGNER_JAVA_VERSION_INCOMPATIBLE'),
    ('nosuchalgorithmexception','KEYSTORE_CRYPTO_ALGORITHM_UNAVAILABLE'),
    ('parsealgparameters','KEYSTORE_ALGORITHM_PARAMETERS_UNSUPPORTED'),
    ('unsupported key protection algorithm','KEYSTORE_CRYPTO_ALGORITHM_UNAVAILABLE'),
    ('invalid keystore format','KEYSTORE_BASE64_DECODED_FILE_NOT_A_KEYSTORE'),
    ('unrecognized keystore format','KEYSTORE_BASE64_DECODED_FILE_NOT_A_KEYSTORE'),
    ('keystore file does not exist','DECODED_KEYSTORE_FILE_MISSING'),
    ('cannot recover key','KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY'),
    ('get key failed','KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY'),
    ('unrecoverablekey','KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY'),
    ('password was incorrect','EXISTING_KEYSTORE_PASSWORD_REJECTED'),
    ('keystore was tampered','EXISTING_KEYSTORE_PASSWORD_OR_INTEGRITY_REJECTED'),
    ('no key found','ALIAS_NOT_FOUND_IN_EXISTING_KEYSTORE'),
    ('unknown option','SIGNER_OPTION_UNSUPPORTED'),
    ('unexpected parameter','SIGNER_OPTION_UNSUPPORTED')]:
    if needle in text:code=value;break
classes=[name for name in ['NoSuchAlgorithmException','UnrecoverableKeyException','BadPaddingException','IOException','IllegalArgumentException','CertificateException','UnsupportedClassVersionError'] if name.lower() in text]
if phase=='store' and code=='KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY':
    code='EXISTING_KEYSTORE_PASSWORD_OR_INTEGRITY_REJECTED'
print('::error title=Signing diagnostic::'+code+'. No key has been changed; no secret value is printed.')
if classes:print('::notice title=Signing exception categories::'+', '.join(classes))
