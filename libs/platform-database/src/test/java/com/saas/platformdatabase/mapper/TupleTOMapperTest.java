package com.saas.platformdatabase.mapper;

import com.saas.platformdatabase.support.FakeTuple;
import com.saas.platformdatabase.support.SampleUserTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TupleTOMapperTest {

    @Test
    void mapsTupleByAliasIntoTO() {
        UUID userId = UUID.randomUUID();
        FakeTuple tuple = new FakeTuple()
                .with("user_id", userId.toString())
                .with("email", "tuple@example.com")
                .with("active", Boolean.TRUE);

        SampleUserTO to = TupleTOMapper.map(tuple, SampleUserTO.class);

        assertEquals(userId, to.getUserId());
        assertEquals("tuple@example.com", to.getEmail());
        assertEquals(Boolean.TRUE, to.getActive());
    }

    @Test
    void mapListMapsEveryTuple() {
        FakeTuple tuple1 = new FakeTuple().with("email", "a@example.com");
        FakeTuple tuple2 = new FakeTuple().with("email", "b@example.com");

        List<SampleUserTO> tos = TupleTOMapper.mapList(List.of(tuple1, tuple2), SampleUserTO.class);

        assertEquals(2, tos.size());
        assertEquals("a@example.com", tos.get(0).getEmail());
        assertEquals("b@example.com", tos.get(1).getEmail());
    }
}
