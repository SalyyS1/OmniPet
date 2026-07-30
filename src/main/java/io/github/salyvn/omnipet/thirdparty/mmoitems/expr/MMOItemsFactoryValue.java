package io.github.salyvn.omnipet.thirdparty.mmoitems.expr;

import io.github.nahkd123.tinyexpr.Value;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;

public enum MMOItemsFactoryValue implements Value {
	FACTORY {
		@Override
		public Value call(Value[] params) {
			Type type = MMOItems.plugin.getTypes().get(params[0].unwrapAs(String.class));
			String id = params[1].unwrapAs(String.class);
			MMOItemTemplate template = MMOItems.plugin.getTemplates().getTemplate(type, id);
			return new MMOItemBuilderValue(template.newBuilder());
		}
	}
}