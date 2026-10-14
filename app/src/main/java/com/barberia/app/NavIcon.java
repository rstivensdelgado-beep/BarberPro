package com.barberia.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Iconos de trazo dibujados a mano para la barra inferior (sin depender de recursos). */
public class NavIcon extends View {

    public static final int HOME = 0;
    public static final int CALENDAR = 1;
    public static final int CASH = 2;
    public static final int USERS = 3;
    public static final int BOX = 4;
    public static final int GEAR = 5;
    public static final int PHOTO = 6;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NavIcon(Context c, int kind) {
        super(c);
        this.kind = kind;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Ui.dp(c, 1.8f));
        paint.setColor(Ui.MUTED);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float m = s * 0.12f;
        switch (kind) {
            case HOME: {
                Path p = new Path();
                p.moveTo(m, s * 0.46f);
                p.lineTo(s / 2f, m);
                p.lineTo(s - m, s * 0.46f);
                p.moveTo(s * 0.24f, s * 0.40f);
                p.lineTo(s * 0.24f, s - m);
                p.lineTo(s * 0.76f, s - m);
                p.lineTo(s * 0.76f, s * 0.40f);
                cv.drawPath(p, paint);
                break;
            }
            case CALENDAR: {
                cv.drawRoundRect(new RectF(m, s * 0.20f, s - m, s - m), s * 0.08f, s * 0.08f, paint);
                cv.drawLine(m, s * 0.44f, s - m, s * 0.44f, paint);
                cv.drawLine(s * 0.32f, m, s * 0.32f, s * 0.30f, paint);
                cv.drawLine(s * 0.68f, m, s * 0.68f, s * 0.30f, paint);
                break;
            }
            case CASH: {
                cv.drawRoundRect(new RectF(m, s * 0.28f, s - m, s - m), s * 0.08f, s * 0.08f, paint);
                cv.drawCircle(s / 2f, s * 0.60f, s * 0.12f, paint);
                break;
            }
            case USERS: {
                cv.drawCircle(s / 2f, s * 0.34f, s * 0.17f, paint);
                cv.drawArc(new RectF(s * 0.20f, s * 0.56f, s * 0.80f, s * 1.16f), 180, 180, false, paint);
                break;
            }
            case BOX: {
                cv.drawRoundRect(new RectF(m, s * 0.34f, s - m, s - m), s * 0.06f, s * 0.06f, paint);
                cv.drawRoundRect(new RectF(m, s * 0.14f, s - m, s * 0.34f), s * 0.04f, s * 0.04f, paint);
                break;
            }
            case GEAR: {
                cv.drawCircle(s / 2f, s / 2f, s * 0.16f, paint);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4.0;
                    float x1 = (float) (s / 2f + Math.cos(a) * s * 0.28f);
                    float y1 = (float) (s / 2f + Math.sin(a) * s * 0.28f);
                    float x2 = (float) (s / 2f + Math.cos(a) * s * 0.42f);
                    float y2 = (float) (s / 2f + Math.sin(a) * s * 0.42f);
                    cv.drawLine(x1, y1, x2, y2, paint);
                }
                break;
            }
            case PHOTO: {
                cv.drawRoundRect(new RectF(m, s * 0.18f, s - m, s - m), s * 0.08f, s * 0.08f, paint);
                Path p = new Path();
                p.moveTo(m, s * 0.74f);
                p.lineTo(s * 0.40f, s * 0.46f);
                p.lineTo(s * 0.58f, s * 0.64f);
                p.lineTo(s * 0.68f, s * 0.54f);
                p.lineTo(s - m, s * 0.74f);
                cv.drawPath(p, paint);
                cv.drawCircle(s * 0.68f, s * 0.36f, s * 0.07f, paint);
                break;
            }
            default:
                break;
        }
    }
}
