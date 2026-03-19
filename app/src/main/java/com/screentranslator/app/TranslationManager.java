package com.screentranslator.app.utils;

import android.util.Log;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

/**
 * Manages ML Kit translation between supported languages.
 * Supports: English (en), Urdu (ur), Hindi (hi), Arabic (ar)
 */
public class TranslationManager {

    private static final String TAG = "TranslationManager";

    public interface TranslationCallback {
        void onSuccess(String translatedText, String detectedLanguage);
        void onFailure(String error);
    }

    public interface ModelDownloadCallback {
        void onComplete();
        void onFailure(String error);
    }

    /**
     * Translates text to the target language.
     * Auto-detects the source language using ML Kit Language ID.
     *
     * @param inputText    Text to translate
     * @param targetLang   Target language code (e.g. "ur", "hi", "ar", "en")
     * @param callback     Callback with result
     */
    public void translate(String inputText, String targetLang, TranslationCallback callback) {
        if (inputText == null || inputText.trim().isEmpty()) {
            callback.onFailure("No text to translate");
            return;
        }

        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
        languageIdentifier.identifyLanguage(inputText)
                .addOnSuccessListener(languageCode -> {
                    String sourceLang = languageCode;
                    if ("und".equals(languageCode)) {
                        // Undetermined - default to English
                        sourceLang = TranslateLanguage.ENGLISH;
                    }

                    String mlKitSource = getMlKitCode(sourceLang);
                    String mlKitTarget = getMlKitCode(targetLang);

                    if (mlKitSource.equals(mlKitTarget)) {
                        // Same language — no translation needed, try English as fallback
                        mlKitTarget = getMlKitCode("en");
                    }

                    performTranslation(inputText, mlKitSource, mlKitTarget, sourceLang, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Language detection failed: " + e.getMessage());
                    // Fallback: assume English source
                    String mlKitTarget = getMlKitCode(targetLang);
                    performTranslation(inputText, TranslateLanguage.ENGLISH, mlKitTarget, "en", callback);
                });
    }

    private void performTranslation(String text, String sourceLang, String targetLang,
                                     String detectedLang, TranslationCallback callback) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build();

        Translator translator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translator.translate(text)
                            .addOnSuccessListener(translatedText -> {
                                translator.close();
                                callback.onSuccess(translatedText, detectedLang);
                            })
                            .addOnFailureListener(e -> {
                                translator.close();
                                Log.e(TAG, "Translation failed: " + e.getMessage());
                                callback.onFailure("Translation failed: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    translator.close();
                    Log.e(TAG, "Model download failed: " + e.getMessage());
                    callback.onFailure("Model download failed. Check internet connection.");
                });
    }

    /**
     * Pre-downloads translation models for offline use.
     * Downloads all combinations for: en, ur, hi, ar
     */
    public void preDownloadModels(String targetLang, ModelDownloadCallback callback) {
        String mlKitTarget = getMlKitCode(targetLang);
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(mlKitTarget)
                .build();

        Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translator.close();
                    callback.onComplete();
                })
                .addOnFailureListener(e -> {
                    translator.close();
                    callback.onFailure(e.getMessage());
                });
    }

    /**
     * Maps short language codes to ML Kit TranslateLanguage constants.
     */
    private String getMlKitCode(String langCode) {
        if (langCode == null) return TranslateLanguage.ENGLISH;
        switch (langCode.toLowerCase()) {
            case "ur": return TranslateLanguage.URDU;
            case "hi": return TranslateLanguage.HINDI;
            case "ar": return TranslateLanguage.ARABIC;
            case "en":
            default:   return TranslateLanguage.ENGLISH;
        }
    }
}
