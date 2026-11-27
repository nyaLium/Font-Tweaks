package com.nyalium.fonttweaks;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu 入口：列表里点「Font Tweaks」直接进设置页。 */
public final class FontTweaksModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FontTweaksScreen::new;
    }
}
