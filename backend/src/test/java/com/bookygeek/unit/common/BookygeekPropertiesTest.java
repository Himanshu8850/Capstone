package com.bookygeek.unit.common;

import com.bookygeek.common.props.BookygeekProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookygeekPropertiesTest {

    private static Map<String, String> bands() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("bookygeek.price-bands[0].label", "Under 200");
        m.put("bookygeek.price-bands[0].max-price", "199.99");
        m.put("bookygeek.price-bands[1].label", "200 to 499");
        m.put("bookygeek.price-bands[1].min-price", "200.00");
        m.put("bookygeek.price-bands[1].max-price", "499.99");
        m.put("bookygeek.price-bands[2].label", "1,000 and above");
        m.put("bookygeek.price-bands[2].min-price", "1000.00");
        return m;
    }

    private static BookygeekProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("bookygeek", BookygeekProperties.class).get();
    }

    @Test
    void approvedDefaultsApplyWhenNothingIsConfigured() {
        BookygeekProperties props = bind(bands());
        assertThat(props.rewards().initialPoints()).isEqualTo(1000);
        assertThat(props.rewards().capPercent()).isEqualTo(50);
        assertThat(props.tax().rate()).isEqualByComparingTo(new BigDecimal("0.05"));
        assertThat(props.delivery().charge()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(props.cancellation().windowHours()).isEqualTo(48);
    }

    @Test
    void priceBandsKeepOpenEndedBounds() {
        BookygeekProperties props = bind(bands());
        assertThat(props.priceBands()).hasSize(3);
        assertThat(props.priceBands().get(0).minPrice()).isNull();
        assertThat(props.priceBands().get(0).maxPrice()).isEqualByComparingTo("199.99");
        assertThat(props.priceBands().get(2).maxPrice()).isNull();
    }

    @Test
    void configuredValuesOverrideDefaults() {
        Map<String, String> source = bands();
        source.put("bookygeek.rewards.initial-points", "250");
        source.put("bookygeek.cancellation.window-hours", "24");
        BookygeekProperties props = bind(source);
        assertThat(props.rewards().initialPoints()).isEqualTo(250);
        assertThat(props.cancellation().windowHours()).isEqualTo(24);
    }

    @Test
    void invalidValuesStopStartUp() {
        Map<String, String> negative = bands();
        negative.put("bookygeek.rewards.initial-points", "-1");
        assertThatThrownBy(() -> bind(negative)).isInstanceOf(BindException.class);

        Map<String, String> cap = bands();
        cap.put("bookygeek.rewards.cap-percent", "101");
        assertThatThrownBy(() -> bind(cap)).isInstanceOf(BindException.class);

        Map<String, String> tax = bands();
        tax.put("bookygeek.tax.rate", "1.5");
        assertThatThrownBy(() -> bind(tax)).isInstanceOf(BindException.class);

        Map<String, String> delivery = bands();
        delivery.put("bookygeek.delivery.charge", "-40");
        assertThatThrownBy(() -> bind(delivery)).isInstanceOf(BindException.class);

        Map<String, String> window = bands();
        window.put("bookygeek.cancellation.window-hours", "0");
        assertThatThrownBy(() -> bind(window)).isInstanceOf(BindException.class);
    }

    @Test
    void missingOrInvertedPriceBandsStopStartUp() {
        assertThatThrownBy(() -> bind(Map.of("bookygeek.rewards.initial-points", "1000")))
                .isInstanceOf(RuntimeException.class);

        Map<String, String> inverted = new LinkedHashMap<>();
        inverted.put("bookygeek.price-bands[0].label", "Bad");
        inverted.put("bookygeek.price-bands[0].min-price", "500.00");
        inverted.put("bookygeek.price-bands[0].max-price", "100.00");
        assertThatThrownBy(() -> bind(inverted)).isInstanceOf(BindException.class);
    }
}
