package com.saas.admin.negocio.impl;

import com.saas.admin.dto.PlatformSettingDTO;

import java.util.List;

public interface PlatformSettingsNegocio {

    List<PlatformSettingDTO> list();

    String updateValue(String key, String rawValue, String userId);
}
