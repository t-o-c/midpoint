package com.evolveum.midpoint.repo.sql.data.common;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.repo.sql.data.common.embedded.RPolyString;
import com.evolveum.midpoint.repo.sql.data.common.other.RLookupTableRow;
import com.evolveum.midpoint.repo.sql.util.DtoTranslationException;
import com.evolveum.midpoint.repo.sql.util.IdGeneratorResult;
import com.evolveum.midpoint.repo.sql.util.RUtil;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableRowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableTableType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ForeignKey;

import javax.persistence.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Viliam Repan (lazyman)
 */
@Entity
@ForeignKey(name = "fk_lookup_table")
@Table(uniqueConstraints = @UniqueConstraint(name = "uc_lookup_name", columnNames = {"name_norm"}))
public class RLookupTable extends RObject<LookupTableType> {

    private RPolyString name;
    private Set<RLookupTableRow> rows;

    @OneToMany(mappedBy = "owner", orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    public Set<RLookupTableRow> getRows() {
        if (rows == null) {
            rows = new HashSet<>();
        }
        return rows;
    }

    public void setRows(Set<RLookupTableRow> rows) {
        this.rows = rows;
    }

    @Override
    @Embedded
    public RPolyString getName() {
        return name;
    }

    @Override
    public void setName(RPolyString name) {
        this.name = name;
    }

    public static void copyFromJAXB(LookupTableType jaxb, RLookupTable repo, PrismContext prismContext,
                                    IdGeneratorResult generatorResult) throws DtoTranslationException {
        RObject.copyFromJAXB(jaxb, repo, prismContext, generatorResult);

        repo.setName(RPolyString.copyFromJAXB(jaxb.getName()));

        LookupTableTableType table = jaxb.getTable();
        if (table == null) {
            return;
        }

        for (LookupTableRowType row : table.getRow()) {
            RLookupTableRow rRow = new RLookupTableRow();
            rRow.setOwner(repo);
            rRow.setKey(row.getKey());
            rRow.setLabel(RPolyString.copyFromJAXB(row.getLabel()));
            rRow.setLastChangeTimestamp(row.getLastChangeTimestamp());
            rRow.setValue(row.getValue());

            repo.getRows().add(rRow);
        }
    }

    protected static <T extends ObjectType> void copyToJAXB(RLookupTable repo, LookupTableType jaxb, PrismContext prismContext,
                                                            Collection<SelectorOptions<GetOperationOptions>> options)
            throws DtoTranslationException {

        //todo wtf with this

        RObject.copyToJAXB(repo, jaxb, prismContext, options);

        if (repo.getRows() != null && !repo.getRows().isEmpty()) {


        }
    }

    @Override
    public LookupTableType toJAXB(PrismContext prismContext, Collection<SelectorOptions<GetOperationOptions>> options) throws DtoTranslationException {
        LookupTableType object = new LookupTableType();
        RUtil.revive(object, prismContext);
        RLookupTable.copyToJAXB(this, object, prismContext, options);

        return object;
    }
}
