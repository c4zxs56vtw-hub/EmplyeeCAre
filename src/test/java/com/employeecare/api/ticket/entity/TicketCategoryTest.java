package com.employeecare.api.ticket.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCategoryTest {

    @Test
    void defaultValues_sontConformes() {
        TicketCategory category = new TicketCategory();

        assertThat(category.isActive()).isTrue();
    }

    @Test
    void gettersAndSetters_fonctionnentCorrectement() {
        TicketCategory category = new TicketCategory();

        category.setCode("RH");
        category.setLabel("Ressources humaines");
        category.setActive(false);

        assertThat(category.getCode()).isEqualTo("RH");
        assertThat(category.getLabel()).isEqualTo("Ressources humaines");
        assertThat(category.isActive()).isFalse();
    }
}
