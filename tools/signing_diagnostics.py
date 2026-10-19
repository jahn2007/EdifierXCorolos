#!/usr/bin/env python3
"""Emit only fixed failure codes, never text containing alias/password/key material."""
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text(errors='replace').lower()
code='APKSIGNER_FAILED_CHECK_SECURE_ACTIONS_LOG'
for needle,value in [('cannot recover key','KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY'),('get key failed','KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY'),('unrecoverablekey','KEY_PASSWORD_CANNOT_UNLOCK_EXISTING_KEY'),('password was incorrect','EXISTING_KEYSTORE_PASSWORD_REJECTED'),('keystore was tampered','EXISTING_KEYSTORE_PASSWORD_OR_INTEGRITY_REJECTED'),('invalid keystore format','KEYSTORE_BASE64_DECODED_FILE_NOT_A_KEYSTORE'),('unrecognized keystore format','KEYSTORE_BASE64_DECODED_FILE_NOT_A_KEYSTORE'),('keystore file does not exist','DECODED_KEYSTORE_FILE_MISSING'),('unsupportedclassversion','SIGNER_JAVA_VERSION_INCOMPATIBLE'),('no key found','ALIAS_NOT_FOUND_IN_EXISTING_KEYSTORE'),('unknown option','SIGNER_OPTION_UNSUPPORTED'),('unexpected parameter','SIGNER_OPTION_UNSUPPORTED')]:
    if needle in text:code=value;break
print('::error title=Signing diagnostic::'+code+'. No key has been changed; no secret value is printed.')
