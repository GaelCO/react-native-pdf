package com.github.barteksc.pdfviewer;

// Declared in AndroidPdfViewer's package to reach PDFView's package-private
// pdfFile/renderingHandler fields and the package-private PdfFile class.
//
// PDFView.recycle() disposes the PdfFile synchronously on the main thread while
// RenderingHandler may still be rendering a page on its own thread, which closes
// the native pdfium document under the renderer (SIGSEGV in libpdfium.so, see
// https://github.com/wonday/react-native-pdf/issues/1024 and
// https://github.com/wonday/react-native-pdf/issues/1041). Posting the dispose on
// the rendering handler serializes it after any in-flight render.
public final class PdfFileDisposer {
    private PdfFileDisposer() {}

    // Detaches the PdfFile so that PDFView.recycle() skips its synchronous dispose,
    // and returns a task disposing it on the rendering thread. Must be called right
    // before super.recycle(), and the returned task run right after it.
    public static Runnable detach(PDFView view) {
        final PdfFile pdfFile = view.pdfFile;
        final RenderingHandler renderingHandler = view.renderingHandler;
        // RenderingHandler.proceed() reads view.pdfFile when it dequeues a task, so drop
        // the queued tasks (as recycle() does) before clearing the field.
        if (renderingHandler != null) {
            renderingHandler.stop();
            renderingHandler.removeMessages(RenderingHandler.MSG_RENDER_TASK);
        }
        view.pdfFile = null;
        if (pdfFile == null) {
            return () -> {};
        }
        return () -> {
            // post() fails once the rendering thread has quit: nothing can be
            // rendering anymore, so disposing on the current thread is safe.
            if (renderingHandler == null || !renderingHandler.post(pdfFile::dispose)) {
                pdfFile.dispose();
            }
        };
    }
}
