package net.runelite.client.plugins.microbot.inventorysetups.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;

import java.io.IOException;

public class InventorySetupItemSerializableTypeAdapter extends TypeAdapter<InventorySetupItemSerializable>
{
	@Override
	public void write(JsonWriter out, InventorySetupItemSerializable iss) throws IOException
	{
		if (iss == null)
		{
			out.setSerializeNulls(true);
			out.nullValue();
			out.setSerializeNulls(false);
		}
		else
		{
			out.beginObject();
			out.name("id");
			out.value(iss.getId());
			if (iss.getQ() != null)
			{
				out.name("q");
				out.value(iss.getQ());
			}
			if (iss.getF() != null)
			{
				out.name("f");
				out.value(iss.getF());
			}
			if (iss.getSc() != null)
			{
				out.name("sc");
				out.value(iss.getSc().toString());
			}
			out.endObject();
		}

	}

	@Override
	public InventorySetupItemSerializable read(JsonReader in) throws IOException
	{
		if (in.peek() == JsonToken.NULL)
		{
			in.nextNull();
			return null;
		}

		int id = -1;
		Integer q = null;
		Boolean f = null;
		InventorySetupsStackCompareID sc = null;

		in.beginObject();
		while (in.hasNext())
		{
			JsonToken token = in.peek();
			if (token.equals(JsonToken.NAME))
			{
				//get the current token
				String fieldName = in.nextName();
				switch (fieldName)
				{
					case "id":
						id = in.nextInt();
						break;
					case "q":
						q = in.nextInt();
						break;
					case "f":
						f = in.nextBoolean();
						break;
					case "sc":
						sc = InventorySetupsStackCompareID.valueOf(in.nextString());
						break;
					default:
						break;
				}
			}
		}

		in.endObject();
		return new InventorySetupItemSerializable(id, q, f, sc);
	}
}
