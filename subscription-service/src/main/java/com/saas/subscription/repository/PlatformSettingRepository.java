package com.saas.subscription.repository;

import com.saas.subscription.entity.PlatformSetting;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class PlatformSettingRepository implements PanacheRepositoryBase<PlatformSetting, String> {

    public Optional<String> findValue(String key) {
        return findByIdOptional(key).map(setting -> setting.value);
    }
}
